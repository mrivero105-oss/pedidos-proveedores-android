-- ============================================================================
-- Isaac POS -> Pedidos Proveedores (Supabase PostgreSQL 17)
-- Crea tablas propias de proveedores y relación producto-proveedor sin alterar
-- ninguna tabla que escribe el servidor PC de Isaac POS (cloud_productos).
--
-- Cómo aplicar:
--   1. Abre tu proyecto en Supabase -> SQL Editor -> New query
--   2. Pega y ejecuta este script completo (es 100% idempotente).
-- ============================================================================

create table if not exists public.cloud_proveedores (
  id bigint generated always as identity primary key,
  nombre text not null,
  telefono_whatsapp text not null default '',
  notas text not null default '',
  activo boolean not null default true,
  creado_el timestamptz not null default now(),
  actualizado_el timestamptz not null default now(),
  constraint cloud_proveedores_nombre_nonempty check (length(trim(nombre)) > 0)
);

create unique index if not exists cloud_proveedores_nombre_lower_uidx
  on public.cloud_proveedores (lower(trim(nombre)));

create table if not exists public.cloud_producto_proveedor (
  id bigint generated always as identity primary key,
  producto_id character varying not null references public.cloud_productos(id) on delete cascade,
  proveedor_id bigint not null references public.cloud_proveedores(id) on delete cascade,
  unidad_pedido text not null default 'u',
  costo_unitario numeric(12, 2),
  stock_minimo numeric(12, 2) not null default 5,
  es_principal boolean not null default true,
  actualizado_el timestamptz not null default now(),
  constraint cloud_producto_proveedor_unique unique (producto_id, proveedor_id),
  constraint cloud_producto_proveedor_costo_nonneg check (costo_unitario is null or costo_unitario >= 0),
  constraint cloud_producto_proveedor_stock_min_nonneg check (stock_minimo >= 0)
);

-- Índices en ambas claves foráneas (mejores prácticas PostgreSQL para JOINs y CASCADE)
create index if not exists cloud_producto_proveedor_producto_id_idx
  on public.cloud_producto_proveedor (producto_id);

create index if not exists cloud_producto_proveedor_proveedor_id_idx
  on public.cloud_producto_proveedor (proveedor_id);

-- Vista de solo lectura ordenada por proveedor que respeta RLS (security_invoker = true)
create or replace view public.v_isaac_pos_productos_por_proveedor
with (security_invoker = true) as
select
  p.id as producto_id,
  p.sucursal_id,
  coalesce(p.codigo_barra, '') as codigo_barra,
  p.nombre as producto_nombre,
  coalesce(p.categoria, '') as categoria,
  p.precio_venta,
  p.stock_actual,
  p.actualizado_el as producto_actualizado_el,
  pr.id as proveedor_id,
  pr.nombre as proveedor_nombre,
  coalesce(pr.telefono_whatsapp, '') as proveedor_telefono,
  coalesce(pp.unidad_pedido, 'u') as unidad_pedido,
  pp.costo_unitario,
  coalesce(pp.stock_minimo, 5) as stock_minimo,
  coalesce(pp.es_principal, true) as es_principal
from public.cloud_productos p
left join public.cloud_producto_proveedor pp
  on pp.producto_id = p.id
left join public.cloud_proveedores pr
  on pr.id = pp.proveedor_id and pr.activo = true;

-- RLS estrictamente de solo lectura para la app Android (anon / authenticated)
alter table public.cloud_proveedores enable row level security;
alter table public.cloud_producto_proveedor enable row level security;

grant select on public.cloud_proveedores to anon, authenticated;
grant select on public.cloud_producto_proveedor to anon, authenticated;
grant select on public.v_isaac_pos_productos_por_proveedor to anon, authenticated;

do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'public'
      and tablename = 'cloud_proveedores'
      and policyname = 'App Movil Lectura Proveedores'
  ) then
    create policy "App Movil Lectura Proveedores"
      on public.cloud_proveedores
      for select
      to anon, authenticated
      using (true);
  end if;

  if not exists (
    select 1 from pg_policies
    where schemaname = 'public'
      and tablename = 'cloud_producto_proveedor'
      and policyname = 'App Movil Lectura Producto Proveedor'
  ) then
    create policy "App Movil Lectura Producto Proveedor"
      on public.cloud_producto_proveedor
      for select
      to anon, authenticated
      using (true);
  end if;
end $$;
