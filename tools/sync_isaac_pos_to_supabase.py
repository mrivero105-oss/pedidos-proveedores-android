#!/usr/bin/env python3
"""
Sincronizador Isaac POS (PC local) -> Supabase (Pedidos Proveedores).

Fuentes soportadas (en orden automático de prioridad):
  1. PostgreSQL 16 local de Isaac POS en modo SOLO LECTURA vía psql.exe
     (si LOCAL_PG_DB y LOCAL_PG_USER están configurados en tools/.env).
  2. Fallback automático al respaldo más reciente en C:\\IsaacPOS_Respaldos
     (master_snapshot_*.zip o backup_pg_*.sql).

Reglas garantizadas:
  - Stock real tomado de Products.stockQuantity (nunca negativo).
  - Consolida proveedores duplicados conocidos (ej. "REPRESENTACIONES Y
    SERVICIOS PAN (SERVICPAN), C.A." -> "Distribuidora SERVICPAN").
  - Política de fusión NO destructiva en Supabase: nunca ejecuta DELETE ni
    TRUNCATE sobre cloud_productos, ni borra/sobrescribe asignaciones manuales
    de proveedor hechas por el usuario en Supabase o la app Android.

Modos de uso:
  1. Prueba sin escribir nada (--dry-run):
     python tools/sync_isaac_pos_to_supabase.py --dry-run

  2. Subir cambios directo a Supabase (requiere tools/.env):
     python tools/sync_isaac_pos_to_supabase.py --push-rest

  3. Exportar SQL idempotente para Supabase SQL Editor:
     python tools/sync_isaac_pos_to_supabase.py --export-sql supabase/local_sync_isaac_pos_stock_y_proveedores.sql
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
import zipfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


DEFAULT_BACKUP_DIR = Path(r"C:\IsaacPOS_Respaldos")
DEFAULT_PSQL_PATH = Path(r"C:\Program Files\PostgreSQL\16\bin\psql.exe")
DEFAULT_COMPANY_ID = "2"

# Alias conocidos para consolidar proveedores duplicados de facturas importadas
SUPPLIER_ALIASES: dict[str, str] = {
    "representaciones y servicios pan (servicpan), c.a.": "Distribuidora SERVICPAN",
}

PG_READONLY_QUERY = r"""
SET default_transaction_read_only = on;
SELECT json_build_object(
  'products', COALESCE((
    SELECT json_agg(json_build_object(
      'id', "id",
      'name', "name",
      'barcode', "barcode",
      'category', "category",
      'price', "price",
      'stock', "stock",
      'stockQuantity', "stockQuantity",
      'cost', "cost",
      'bulkUnitName', "bulkUnitName",
      'unitsPerBulk', "unitsPerBulk",
      'bulkCost', "bulkCost",
      'minStock', "minStock",
      'supplierId', "supplierId",
      'companyId', "companyId"
    ))
    FROM public."Products"
  ), '[]'::json),
  'suppliers', COALESCE((
    SELECT json_agg(json_build_object(
      'id', "id",
      'name', "name",
      'phone', "phone",
      'contact', "contact",
      'notes', "notes",
      'address', "address",
      'companyId', "companyId",
      'isActive', "isActive"
    ))
    FROM public."Suppliers"
  ), '[]'::json),
  'mappings', COALESCE((
    SELECT json_agg(json_build_object(
      'localProductId', "localProductId",
      'supplierId', "supplierId",
      'updatedAt', "updatedAt"
    ))
    FROM public."SupplierProductMappings"
  ), '[]'::json),
  'purchase_orders', COALESCE((
    SELECT json_agg(json_build_object(
      'supplierId', "supplierId",
      'items', "items",
      'createdAt', "createdAt"
    ))
    FROM public."PurchaseOrders"
  ), '[]'::json),
  'presentations', COALESCE((
    SELECT json_agg(json_build_object(
      'productId', "productId",
      'name', "name",
      'factorConversion', "factorConversion",
      'cost', "cost",
      'isDefaultPurchase', "isDefaultPurchase"
    ))
    FROM public.product_presentaciones
  ), '[]'::json)
)::text;
"""


@dataclass
class LocalProduct:
    id: str
    nombre: str
    codigo_barra: str
    categoria: str
    precio_venta: float
    stock_real: float
    costo_unitario: float | None
    unidad_pedido: str
    stock_minimo: float
    proveedor_nombre: str | None


@dataclass
class LocalSupplier:
    nombre: str
    telefono_whatsapp: str
    notas: str
    activo: bool


def log_line(message: str, log_file: Path | None) -> None:
    stamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    line = f"[{stamp}] {message}"
    print(line)
    if log_file is None:
        return
    try:
        log_file.parent.mkdir(parents=True, exist_ok=True)
        if log_file.exists() and log_file.stat().st_size > 1_048_576:
            backup_log = log_file.with_suffix(".log.old")
            if backup_log.exists():
                backup_log.unlink()
            log_file.rename(backup_log)
        with log_file.open("a", encoding="utf-8") as fh:
            fh.write(line + "\n")
    except Exception:
        pass


def unescape_pg_copy(value: str) -> str:
    out: list[str] = []
    i = 0
    n = len(value)
    while i < n:
        if value[i] == "\\" and i + 1 < n:
            nxt = value[i + 1]
            if nxt == "t":
                out.append("\t")
            elif nxt == "n":
                out.append("\n")
            elif nxt == "r":
                out.append("\r")
            elif nxt == "\\":
                out.append("\\")
            else:
                out.append(nxt)
            i += 2
        else:
            out.append(value[i])
            i += 1
    return "".join(out)


def parse_copy_table(sql_text: str, table_pattern: str) -> list[dict[str, Any]]:
    pattern = rf"COPY\s+public\.{table_pattern}\s*\(([^)]+)\)\s+FROM stdin;\r?\n(.*?)\r?\n\\\."
    match = re.search(pattern, sql_text, flags=re.S)
    if not match:
        return []
    cols = [c.strip().strip('"') for c in match.group(1).split(",")]
    rows: list[dict[str, Any]] = []
    for line in match.group(2).splitlines():
        if not line.strip():
            continue
        vals = [None if v == r"\N" else unescape_pg_copy(v) for v in line.split("\t")]
        rows.append(dict(zip(cols, vals)))
    return rows


def to_float(raw: Any, default: float = 0.0) -> float:
    if raw is None or raw == "":
        return default
    try:
        return float(raw)
    except (TypeError, ValueError):
        return default


def is_truthy(raw: Any, default: bool = True) -> bool:
    if raw is None:
        return default
    if isinstance(raw, bool):
        return raw
    s = str(raw).strip().lower()
    if s in ("f", "false", "0", "no"):
        return False
    if s in ("t", "true", "1", "yes"):
        return True
    return default


def clean_text(raw: Any) -> str:
    if raw is None:
        return ""
    return re.sub(r"\s+", " ", str(raw).replace("\x00", "")).strip()


def canonical_supplier_name(raw_name: str) -> str:
    clean = clean_text(raw_name)
    return SUPPLIER_ALIASES.get(clean.lower(), clean)


def format_factor(val: float) -> str:
    if abs(val - round(val)) < 1e-6:
        return str(int(round(val)))
    return f"{val:.2f}".rstrip("0").rstrip(".")


def load_dotenv(env_path: Path) -> None:
    if not env_path.exists():
        return
    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, val = line.split("=", 1)
        os.environ.setdefault(key.strip(), val.strip().strip('"').strip("'"))


def fetch_raw_from_local_postgres() -> tuple[dict[str, list[dict[str, Any]]] | None, str]:
    pg_db = os.environ.get("LOCAL_PG_DB", "").strip()
    pg_user = os.environ.get("LOCAL_PG_USER", "").strip()
    pg_host = os.environ.get("LOCAL_PG_HOST", "127.0.0.1").strip()
    pg_port = os.environ.get("LOCAL_PG_PORT", "5432").strip()
    pg_pass = os.environ.get("LOCAL_PG_PASSWORD", "")
    psql_bin = Path(os.environ.get("LOCAL_PG_PSQL", str(DEFAULT_PSQL_PATH)).strip())

    if not pg_db or not pg_user or not psql_bin.exists():
        return None, ""

    env = os.environ.copy()
    if pg_pass:
        env["PGPASSWORD"] = pg_pass
    env["PGCLIENTENCODING"] = "UTF8"

    cmd = [
        str(psql_bin),
        "-h", pg_host,
        "-p", pg_port,
        "-U", pg_user,
        "-d", pg_db,
        "-X",
        "-q",
        "-t",
        "-A",
        "-v", "ON_ERROR_STOP=1",
    ]
    proc = subprocess.run(
        cmd,
        input=PG_READONLY_QUERY,
        text=True,
        encoding="utf-8",
        capture_output=True,
        env=env,
        timeout=30,
    )
    if proc.returncode != 0:
        err_preview = clean_text(proc.stderr)[:200]
        raise RuntimeError(f"PostgreSQL local devolvió error (código {proc.returncode}): {err_preview}")

    json_lines = [ln.strip() for ln in proc.stdout.splitlines() if ln.strip().startswith("{")]
    if not json_lines:
        raise RuntimeError("PostgreSQL local no devolvió JSON esperado.")
    data = json.loads(json_lines[-1])
    return data, f"PostgreSQL 16 local ({pg_host}:{pg_port}/{pg_db}, solo lectura)"


def find_latest_backup(backup_dir: Path) -> Path:
    if not backup_dir.exists():
        raise FileNotFoundError(f"No se encontró la carpeta de respaldos de Isaac POS: {backup_dir}")
    candidates: list[Path] = []
    for pattern in ("master_snapshot_*.zip", "backup_pg_*.sql"):
        candidates.extend(backup_dir.glob(pattern))
    if not candidates:
        raise FileNotFoundError(f"No se encontraron respaldos master_snapshot_*.zip ni backup_pg_*.sql en {backup_dir}")
    candidates.sort(key=lambda p: p.stat().st_mtime, reverse=True)
    return candidates[0]


def fetch_raw_from_backup(source_path: Path) -> tuple[dict[str, list[dict[str, Any]]], str]:
    if source_path.suffix.lower() == ".zip":
        with zipfile.ZipFile(source_path, "r") as zf:
            sql_entries = [n for n in zf.namelist() if n.lower().endswith(".sql")]
            if not sql_entries:
                raise RuntimeError(f"El ZIP {source_path} no contiene archivo .sql")
            sql_name = sql_entries[0]
            sql_text = zf.read(sql_name).decode("utf-8", errors="replace")
            label = f"{source_path.name} -> {sql_name}"
    else:
        sql_text = source_path.read_text(encoding="utf-8", errors="replace")
        label = source_path.name

    return (
        {
            "products": parse_copy_table(sql_text, '"Products"'),
            "suppliers": parse_copy_table(sql_text, '"Suppliers"'),
            "mappings": parse_copy_table(sql_text, '"SupplierProductMappings"'),
            "purchase_orders": parse_copy_table(sql_text, '"PurchaseOrders"'),
            "presentations": parse_copy_table(sql_text, "product_presentaciones"),
        },
        label,
    )


def build_normalized_dataset(
    raw: dict[str, list[dict[str, Any]]],
    company_id: str = DEFAULT_COMPANY_ID,
) -> tuple[list[LocalProduct], list[LocalSupplier], dict[str, Any]]:
    raw_products = raw.get("products") or []
    raw_suppliers = raw.get("suppliers") or []
    raw_mappings = raw.get("mappings") or []
    raw_pos_orders = raw.get("purchase_orders") or []
    raw_presentations = raw.get("presentations") or []

    if not raw_products:
        raise RuntimeError('No se encontraron productos en public."Products".')

    # 1. Consolidar proveedores por nombre canónico (ej. los dos SERVICPAN -> Distribuidora SERVICPAN)
    canonical_by_local_id: dict[str, str] = {}
    suppliers_by_lower: dict[str, LocalSupplier] = {}

    for s in raw_suppliers:
        s_comp = s.get("companyId")
        if company_id and s_comp not in (company_id, None, ""):
            continue
        raw_name = clean_text(s.get("name"))
        if not raw_name:
            continue
        canon_name = canonical_supplier_name(raw_name)
        sid = clean_text(s.get("id"))
        if sid:
            canonical_by_local_id[sid] = canon_name

        notes_parts: list[str] = []
        contact = clean_text(s.get("contact"))
        notes = clean_text(s.get("notes"))
        address = clean_text(s.get("address"))
        if contact:
            notes_parts.append(f"Contacto: {contact}")
        if notes:
            notes_parts.append(notes)
        if address:
            notes_parts.append(address)
        phone = clean_text(s.get("phone"))
        joined_notes = " · ".join(notes_parts)
        active = is_truthy(s.get("isActive"), default=True)

        key = canon_name.lower()
        existing = suppliers_by_lower.get(key)
        if existing is None:
            suppliers_by_lower[key] = LocalSupplier(
                nombre=canon_name,
                telefono_whatsapp=phone,
                notas=joined_notes,
                activo=active,
            )
        else:
            # Conservar teléfono y notas más completos al consolidar alias
            if not existing.telefono_whatsapp and phone:
                existing.telefono_whatsapp = phone
            if not existing.notas and joined_notes:
                existing.notas = joined_notes
            existing.activo = existing.activo or active

    suppliers = sorted(suppliers_by_lower.values(), key=lambda x: x.nombre.lower())

    # 2. Proveedores desde PurchaseOrders (más reciente primero)
    po_supplier_by_prod: dict[str, str] = {}
    sorted_pos = sorted(raw_pos_orders, key=lambda r: str(r.get("createdAt") or ""), reverse=True)
    for po in sorted_pos:
        sid = clean_text(po.get("supplierId"))
        canon = canonical_by_local_id.get(sid)
        if not canon:
            continue
        raw_items = po.get("items")
        if isinstance(raw_items, str):
            try:
                parsed = json.loads(raw_items)
                if isinstance(parsed, str):
                    parsed = json.loads(parsed)
            except Exception:
                continue
        elif isinstance(raw_items, list):
            parsed = raw_items
        else:
            continue
        if not isinstance(parsed, list):
            continue
        for item in parsed:
            if not isinstance(item, dict):
                continue
            pid = clean_text(item.get("productId") or item.get("id"))
            if pid and pid not in po_supplier_by_prod:
                po_supplier_by_prod[pid] = canon

    # 3. Proveedores desde SupplierProductMappings (más reciente primero)
    map_supplier_by_prod: dict[str, str] = {}
    sorted_maps = sorted(raw_mappings, key=lambda r: str(r.get("updatedAt") or ""), reverse=True)
    for mp in sorted_maps:
        pid = clean_text(mp.get("localProductId"))
        sid = clean_text(mp.get("supplierId"))
        canon = canonical_by_local_id.get(sid)
        if pid and canon and pid not in map_supplier_by_prod:
            map_supplier_by_prod[pid] = canon

    # 4. Presentaciones de compra
    pres_by_prod: dict[str, list[dict[str, Any]]] = {}
    for pr in raw_presentations:
        pid = clean_text(pr.get("productId"))
        if pid:
            pres_by_prod.setdefault(pid, []).append(pr)

    # 5. Productos reales
    products: list[LocalProduct] = []
    seen_ids: set[str] = set()
    skipped_other_company = 0

    for p in raw_products:
        if company_id and str(p.get("companyId") or "") != company_id:
            skipped_other_company += 1
            continue
        pid = clean_text(p.get("id"))[:100]
        nombre = clean_text(p.get("name"))[:255]
        if not pid or not nombre or pid in seen_ids:
            continue
        seen_ids.add(pid)

        raw_sq = p.get("stockQuantity")
        stock_val = to_float(raw_sq if raw_sq is not None else p.get("stock"), 0.0)
        stock_real = min(round(max(0.0, stock_val), 2), 9999999999.99)

        precio_venta = min(round(max(0.0, to_float(p.get("price"), 0.0)), 2), 999999999999.99)
        unit_cost = to_float(p.get("cost"), 0.0)
        bulk_name = clean_text(p.get("bulkUnitName"))
        units_per_bulk = to_float(p.get("unitsPerBulk"), 0.0)
        bulk_cost = to_float(p.get("bulkCost"), 0.0)
        min_stock_raw = to_float(p.get("minStock"), 0.0)
        stock_minimo = min(round(min_stock_raw if min_stock_raw > 0 else 5.0, 2), 9999999999.99)

        unidad_pedido = "u"
        costo_unitario: float | None = round(unit_cost, 2) if unit_cost > 0 else None

        default_purchase_pres = next(
            (
                pr
                for pr in pres_by_prod.get(pid, [])
                if is_truthy(pr.get("isDefaultPurchase"), default=False)
                and to_float(pr.get("factorConversion"), 0.0) > 1
            ),
            None,
        )
        if default_purchase_pres is not None:
            pr_name = clean_text(default_purchase_pres.get("name")) or "Bulto"
            pr_factor = to_float(default_purchase_pres.get("factorConversion"), 1.0)
            pr_cost = to_float(default_purchase_pres.get("cost"), 0.0)
            unidad_pedido = f"{pr_name} x {format_factor(pr_factor)}"
            if pr_cost > 0:
                costo_unitario = round(pr_cost, 2)
            elif bulk_cost > 0:
                costo_unitario = round(bulk_cost, 2)
            elif unit_cost > 0:
                costo_unitario = round(unit_cost * pr_factor, 2)
        elif bulk_name and units_per_bulk > 1:
            unidad_pedido = f"{bulk_name} x {format_factor(units_per_bulk)}"
            if bulk_cost > 0:
                costo_unitario = round(bulk_cost, 2)
            elif unit_cost > 0:
                costo_unitario = round(unit_cost * units_per_bulk, 2)

        if costo_unitario is not None:
            costo_unitario = min(max(0.0, costo_unitario), 9999999999.99)

        direct_sup = canonical_by_local_id.get(clean_text(p.get("supplierId")))
        chosen_sup = direct_sup or po_supplier_by_prod.get(pid) or map_supplier_by_prod.get(pid)

        products.append(
            LocalProduct(
                id=pid,
                nombre=nombre,
                codigo_barra=clean_text(p.get("barcode"))[:100],
                categoria=(clean_text(p.get("category")) or "General")[:100],
                precio_venta=precio_venta,
                stock_real=stock_real,
                costo_unitario=costo_unitario,
                unidad_pedido=unidad_pedido,
                stock_minimo=stock_minimo,
                proveedor_nombre=chosen_sup,
            )
        )

    counts_by_sup: dict[str, int] = {}
    for pr in products:
        if pr.proveedor_nombre:
            counts_by_sup[pr.proveedor_nombre] = counts_by_sup.get(pr.proveedor_nombre, 0) + 1

    stats = {
        "total_products": len(products),
        "skipped_other_company": skipped_other_company,
        "with_positive_stock": sum(1 for pr in products if pr.stock_real > 0),
        "with_zero_stock": sum(1 for pr in products if pr.stock_real <= 0),
        "total_suppliers": len(suppliers),
        "linked_products": sum(1 for pr in products if pr.proveedor_nombre is not None),
        "unlinked_products": sum(1 for pr in products if pr.proveedor_nombre is None),
        "counts_by_supplier": counts_by_sup,
    }
    return products, suppliers, stats


def sql_str(val: str) -> str:
    return "'" + val.replace("'", "''") + "'"


def sql_num(val: float | None) -> str:
    if val is None:
        return "NULL::numeric"
    return f"{val:.2f}::numeric"


def generate_sync_sql(
    products: list[LocalProduct],
    suppliers: list[LocalSupplier],
    source_label: str,
    stats: dict[str, Any],
) -> str:
    now_iso = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    lines: list[str] = [
        "-- ============================================================================",
        "-- Sincronización Isaac POS (PC local) -> Supabase (Stock real + Proveedores)",
        f"-- Fuente: {source_label}",
        f"-- Generado (UTC): {now_iso}",
        f"-- Resumen: {stats['total_products']} productos ({stats['with_positive_stock']} con stock > 0), "
        f"{stats['total_suppliers']} proveedores consolidados, {stats['linked_products']} vinculados.",
        "-- Seguro, no destructivo e idempotente (sin DELETE ni TRUNCATE sobre cloud_productos).",
        "-- ============================================================================",
        "",
        "begin;",
        "",
        "insert into public.cloud_productos (id, sucursal_id, codigo_barra, nombre, categoria, precio_venta, stock_actual, actualizado_el)",
        "values",
    ]

    lines.append(
        ",\n".join(
            f"  ({sql_str(p.id)}, 'principal', {sql_str(p.codigo_barra)}, {sql_str(p.nombre)}, "
            f"{sql_str(p.categoria)}, {sql_num(p.precio_venta)}, {sql_num(p.stock_real)}, now())"
            for p in products
        )
    )
    lines.extend(
        [
            "on conflict (id) do update set",
            "  codigo_barra = excluded.codigo_barra,",
            "  nombre = excluded.nombre,",
            "  categoria = excluded.categoria,",
            "  precio_venta = excluded.precio_venta,",
            "  stock_actual = excluded.stock_actual,",
            "  actualizado_el = now();",
            "",
            "with src_prov (nombre, telefono_whatsapp, notas, activo) as (",
            "  values",
        ]
    )

    lines.append(
        ",\n".join(
            f"    ({sql_str(s.nombre)}, {sql_str(s.telefono_whatsapp)}, {sql_str(s.notas)}, {'true' if s.activo else 'false'})"
            for s in suppliers
        )
    )
    lines.extend(
        [
            "),",
            "updated_prov as (",
            "  update public.cloud_proveedores cp",
            "  set telefono_whatsapp = case when sp.telefono_whatsapp <> '' then sp.telefono_whatsapp else cp.telefono_whatsapp end,",
            "      notas = case when sp.notas <> '' then sp.notas else cp.notas end,",
            "      activo = sp.activo,",
            "      actualizado_el = now()",
            "  from src_prov sp",
            "  where lower(trim(cp.nombre)) = lower(trim(sp.nombre))",
            "  returning cp.id",
            ")",
            "insert into public.cloud_proveedores (nombre, telefono_whatsapp, notas, activo, creado_el, actualizado_el)",
            "select sp.nombre, sp.telefono_whatsapp, sp.notas, sp.activo, now(), now()",
            "from src_prov sp",
            "where not exists (",
            "  select 1 from public.cloud_proveedores cp where lower(trim(cp.nombre)) = lower(trim(sp.nombre))",
            ");",
            "",
            "-- Fusión no destructiva: solo inserta/actualiza si el producto aún no tiene otro proveedor manual distinto",
            "with src_rel (producto_id, proveedor_nombre, unidad_pedido, costo_unitario, stock_minimo) as (",
            "  values",
        ]
    )

    lines.append(
        ",\n".join(
            f"    ({sql_str(p.id)}, {sql_str(p.proveedor_nombre)}, {sql_str(p.unidad_pedido)}, "
            f"{sql_num(p.costo_unitario)}, {sql_num(p.stock_minimo)})"
            for p in products
            if p.proveedor_nombre
        )
    )
    lines.extend(
        [
            ")",
            "insert into public.cloud_producto_proveedor (producto_id, proveedor_id, unidad_pedido, costo_unitario, stock_minimo, es_principal, actualizado_el)",
            "select r.producto_id, pr.id, r.unidad_pedido, r.costo_unitario, r.stock_minimo, true, now()",
            "from src_rel r",
            "join public.cloud_proveedores pr on lower(trim(pr.nombre)) = lower(trim(r.proveedor_nombre))",
            "join public.cloud_productos p on p.id = r.producto_id",
            "where not exists (",
            "  select 1 from public.cloud_producto_proveedor ex",
            "  where ex.producto_id = r.producto_id and ex.proveedor_id <> pr.id",
            ")",
            "on conflict (producto_id, proveedor_id) do update set",
            "  unidad_pedido = excluded.unidad_pedido,",
            "  costo_unitario = excluded.costo_unitario,",
            "  stock_minimo = excluded.stock_minimo,",
            "  es_principal = true,",
            "  actualizado_el = now();",
            "",
            "commit;",
            "",
            "select",
            "  (select count(*) from public.cloud_productos) as total_cloud_productos,",
            "  (select count(*) from public.cloud_productos where stock_actual > 0) as productos_con_stock_real,",
            "  (select count(*) from public.cloud_proveedores where activo = true) as proveedores_activos,",
            "  (select count(*) from public.cloud_producto_proveedor) as productos_vinculados_a_proveedor;",
            "",
        ]
    )
    return "\n".join(lines)


def supabase_rest_request(
    base_url: str,
    service_key: str,
    method: str,
    path: str,
    payload: Any | None = None,
    prefer: str | None = None,
    extra_headers: dict[str, str] | None = None,
) -> Any:
    url = f"{base_url.rstrip('/')}/rest/v1/{path.lstrip('/')}"
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("apikey", service_key)
    # Claves legacy service_role (JWT eyJ...) requieren Authorization: Bearer;
    # las claves modernas sb_secret_... se autentican solo con el header apikey.
    if service_key.startswith("eyJ"):
        req.add_header("Authorization", f"Bearer {service_key}")
    req.add_header("Content-Type", "application/json")
    if prefer:
        req.add_header("Prefer", prefer)
    if extra_headers:
        for hk, hv in extra_headers.items():
            req.add_header(hk, hv)
    try:
        with urllib.request.urlopen(req, timeout=45) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            return json.loads(body) if body.strip() else None
    except urllib.error.HTTPError as err:
        err_body = err.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Error HTTP {err.code} en {path}: {err_body[:400]}") from err


def push_via_rest(
    products: list[LocalProduct],
    suppliers: list[LocalSupplier],
    log_file: Path | None,
) -> None:
    supabase_url = os.environ.get("SUPABASE_URL", "").strip()
    service_key = os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "").strip()
    if not supabase_url or not service_key or service_key.startswith("PEGA_AQUI"):
        raise RuntimeError(
            "Para usar --push-rest crea tools/.env (copiando tools/env.example) con "
            "SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY."
        )

    now_iso = datetime.now(timezone.utc).isoformat()

    # 1. Upsert cloud_productos en lotes de 200 (siempre actualiza stock real y precios, sin borrar nada)
    for start in range(0, len(products), 200):
        batch = [
            {
                "id": p.id,
                "sucursal_id": "principal",
                "codigo_barra": p.codigo_barra,
                "nombre": p.nombre,
                "categoria": p.categoria,
                "precio_venta": p.precio_venta,
                "stock_actual": p.stock_real,
                "actualizado_el": now_iso,
            }
            for p in products[start : start + 200]
        ]
        supabase_rest_request(
            supabase_url,
            service_key,
            "POST",
            "cloud_productos?on_conflict=id",
            payload=batch,
            prefer="resolution=merge-duplicates,return=minimal",
        )
    log_line(f"OK cloud_productos: {len(products)} productos sincronizados.", log_file)

    # 2. Proveedores no destructivos (nunca borra proveedores manuales ni pisa teléfono/notas existentes con vacío)
    existing_provs = supabase_rest_request(
        supabase_url,
        service_key,
        "GET",
        "cloud_proveedores?select=id,nombre,telefono_whatsapp,notas,activo",
    ) or []
    prov_by_lower = {clean_text(r["nombre"]).lower(): r for r in existing_provs}
    cloud_sup_id_by_lower: dict[str, int] = {k: int(v["id"]) for k, v in prov_by_lower.items()}

    for s in suppliers:
        key = s.nombre.lower()
        existing = prov_by_lower.get(key)
        if existing is not None:
            cid = int(existing["id"])
            patch_payload: dict[str, Any] = {
                "activo": s.activo,
                "actualizado_el": now_iso,
            }
            if s.telefono_whatsapp:
                patch_payload["telefono_whatsapp"] = s.telefono_whatsapp
            if s.notas:
                patch_payload["notas"] = s.notas
            supabase_rest_request(
                supabase_url,
                service_key,
                "PATCH",
                f"cloud_proveedores?id=eq.{cid}",
                payload=patch_payload,
                prefer="return=minimal",
            )
            cloud_sup_id_by_lower[key] = cid
        else:
            inserted = supabase_rest_request(
                supabase_url,
                service_key,
                "POST",
                "cloud_proveedores",
                payload={
                    "nombre": s.nombre,
                    "telefono_whatsapp": s.telefono_whatsapp,
                    "notas": s.notas,
                    "activo": s.activo,
                    "actualizado_el": now_iso,
                },
                prefer="return=representation",
            )
            cid = int(inserted[0]["id"])
            cloud_sup_id_by_lower[key] = cid
    log_line(f"OK cloud_proveedores: {len(suppliers)} proveedores consolidados sincronizados.", log_file)

    # 3. Fusión no destructiva de cloud_producto_proveedor:
    #    leer los vínculos existentes para respetar asignaciones manuales a otro proveedor
    existing_rels: list[dict[str, Any]] = []
    offset = 0
    while offset < 25_000:
        page = supabase_rest_request(
            supabase_url,
            service_key,
            "GET",
            "cloud_producto_proveedor?select=producto_id,proveedor_id",
            extra_headers={"Range-Unit": "items", "Range": f"{offset}-{offset + 999}"},
        ) or []
        existing_rels.extend(page)
        if len(page) < 1000:
            break
        offset += 1000

    existing_prov_ids_by_prod: dict[str, set[int]] = {}
    for r in existing_rels:
        existing_prov_ids_by_prod.setdefault(str(r["producto_id"]), set()).add(int(r["proveedor_id"]))

    rels_to_upsert: list[dict[str, Any]] = []
    preserved_manual = 0
    for p in products:
        if not p.proveedor_nombre:
            continue
        cid = cloud_sup_id_by_lower.get(p.proveedor_nombre.lower())
        if cid is None:
            continue
        existing_provs_for_prod = existing_prov_ids_by_prod.get(p.id, set())
        if existing_provs_for_prod and cid not in existing_provs_for_prod:
            # Respetar asignación manual previa en Supabase
            preserved_manual += 1
            continue
        rels_to_upsert.append(
            {
                "producto_id": p.id,
                "proveedor_id": cid,
                "unidad_pedido": p.unidad_pedido,
                "costo_unitario": p.costo_unitario,
                "stock_minimo": p.stock_minimo,
                "es_principal": True,
                "actualizado_el": now_iso,
            }
        )

    for start in range(0, len(rels_to_upsert), 200):
        supabase_rest_request(
            supabase_url,
            service_key,
            "POST",
            "cloud_producto_proveedor?on_conflict=producto_id,proveedor_id",
            payload=rels_to_upsert[start : start + 200],
            prefer="resolution=merge-duplicates,return=minimal",
        )
    log_line(
        f"OK cloud_producto_proveedor: {len(rels_to_upsert)} vínculos actualizados "
        f"({preserved_manual} asignaciones manuales preservadas).",
        log_file,
    )


def main() -> int:
    script_dir = Path(__file__).resolve().parent
    load_dotenv(script_dir / ".env")

    parser = argparse.ArgumentParser(description="Sincroniza stock real y proveedores desde Isaac POS hacia Supabase.")
    parser.add_argument(
        "--backup-dir",
        type=Path,
        default=Path(os.environ.get("ISAAC_POS_BACKUP_DIR", str(DEFAULT_BACKUP_DIR))),
        help="Carpeta fallback de respaldos de Isaac POS.",
    )
    parser.add_argument("--source", type=Path, default=None, help="Forzar lectura desde un .zip o .sql específico.")
    parser.add_argument("--skip-local-pg", action="store_true", help="Omitir PostgreSQL local y leer solo respaldos.")
    parser.add_argument("--export-sql", type=Path, default=None, help="Exportar archivo SQL idempotente.")
    parser.add_argument("--push-rest", action="store_true", help="Subir directo a Supabase usando tools/.env.")
    parser.add_argument("--dry-run", action="store_true", help="Validar lectura y conteos sin escribir archivos ni subir.")
    parser.add_argument("--log-file", type=Path, default=script_dir / "logs" / "sync.log", help="Ruta del archivo de log.")
    args = parser.parse_args()

    company_id = os.environ.get("ISAAC_POS_COMPANY_ID", DEFAULT_COMPANY_ID).strip() or DEFAULT_COMPANY_ID
    log_file: Path | None = None if args.dry_run else args.log_file

    raw_data: dict[str, list[dict[str, Any]]] | None = None
    source_label = ""

    if args.source is None and not args.skip_local_pg:
        try:
            raw_data, source_label = fetch_raw_from_local_postgres()
        except Exception as pg_err:
            log_line(f"Aviso: fallo lectura PostgreSQL local ({pg_err}); usando fallback de respaldos...", log_file)
            raw_data = None

    if raw_data is None:
        source_path = args.source if args.source else find_latest_backup(args.backup_dir)
        raw_data, source_label = fetch_raw_from_backup(source_path)

    products, suppliers, stats = build_normalized_dataset(raw_data, company_id=company_id)

    log_line(f"Fuente activa: {source_label}", log_file)
    log_line(
        f"Resumen Isaac POS: {stats['total_products']} productos reales "
        f"({stats['with_positive_stock']} con stock > 0, {stats['with_zero_stock']} en 0) | "
        f"{stats['total_suppliers']} proveedores consolidados | {stats['linked_products']} vinculados.",
        log_file,
    )

    if args.dry_run:
        for sup_name, count in sorted(stats["counts_by_supplier"].items(), key=lambda kv: -kv[1]):
            print(f"  - {sup_name}: {count} productos")
        return 0

    if args.push_rest:
        push_via_rest(products, suppliers, log_file)
        return 0

    out_path = args.export_sql or (
        script_dir.parent / "supabase" / "local_sync_isaac_pos_stock_y_proveedores.sql"
    )
    out_path.parent.mkdir(parents=True, exist_ok=True)
    sql_output = generate_sync_sql(products, suppliers, source_label, stats)
    out_path.write_text(sql_output, encoding="utf-8")
    size_kb = out_path.stat().st_size / 1024.0
    log_line(f"SQL generado: {out_path} ({size_kb:.1f} KB)", log_file)
    return 0


if __name__ == "__main__":
    sys.exit(main())
