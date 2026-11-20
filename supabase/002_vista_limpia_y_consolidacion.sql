-- ============================================================================
-- Pedidos Proveedores - Limpieza no destructiva de vista y consolidación
--
-- Qué hace (100% idempotente, NO borra nada de public.cloud_productos):
--   1. Reemplaza public.v_isaac_pos_productos_por_proveedor (security_invoker)
--      ocultando los registros sintéticos de pruebas E2E / Auditoría.
--   2. Consolida "REPRESENTACIONES Y SERVICIOS PAN (SERVICPAN), C.A." dentro
--      de "Distribuidora SERVICPAN" (56 productos juntos con el WhatsApp de
--      Andreina) y desactiva el duplicado (activo = false).
-- ============================================================================

begin;

-- 1. Vista limpia de solo lectura (sin borrar filas físicas de cloud_productos)
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
  on pr.id = pp.proveedor_id and pr.activo = true
where p.nombre not ilike 'Producto E2E%'
  and p.nombre not ilike 'Producto UI E2E%'
  and p.nombre not ilike 'Producto Auditado Enterprise%'
  and coalesce(p.codigo_barra, '') not ilike 'E2E%'
  and coalesce(p.codigo_barra, '') not ilike 'UIE2E%';

grant select on public.v_isaac_pos_productos_por_proveedor to anon, authenticated;

-- 2. Consolidar "REPRESENTACIONES Y SERVICIOS PAN (SERVICPAN), C.A." -> "Distribuidora SERVICPAN"
do $$
declare
  v_target_id bigint;
  v_dup_id bigint;
begin
  select id into v_target_id
  from public.cloud_proveedores
  where lower(trim(nombre)) = lower('Distribuidora SERVICPAN')
  limit 1;

  select id into v_dup_id
  from public.cloud_proveedores
  where lower(trim(nombre)) = lower('REPRESENTACIONES Y SERVICIOS PAN (SERVICPAN), C.A.')
  limit 1;

  if v_target_id is not null and v_dup_id is not null and v_target_id <> v_dup_id then
    insert into public.cloud_producto_proveedor (
      producto_id, proveedor_id, unidad_pedido, costo_unitario, stock_minimo, es_principal, actualizado_el
    )
    select
      pp.producto_id,
      v_target_id,
      pp.unidad_pedido,
      pp.costo_unitario,
      pp.stock_minimo,
      pp.es_principal,
      now()
    from public.cloud_producto_proveedor pp
    where pp.proveedor_id = v_dup_id
    on conflict (producto_id, proveedor_id) do update set
      unidad_pedido = excluded.unidad_pedido,
      costo_unitario = coalesce(excluded.costo_unitario, public.cloud_producto_proveedor.costo_unitario),
      stock_minimo = excluded.stock_minimo,
      es_principal = true,
      actualizado_el = now();

    delete from public.cloud_producto_proveedor where proveedor_id = v_dup_id;

    update public.cloud_proveedores
    set activo = false,
        notas = trim(both ' · ' from concat_ws(' · ', nullif(notas, ''), 'Consolidado en Distribuidora SERVICPAN')),
        actualizado_el = now()
    where id = v_dup_id;
  end if;
end $$;

commit;

-- Verificación inmediata
select
  coalesce(proveedor_nombre, 'SIN PROVEEDOR') as proveedor,
  count(*) as total,
  count(*) filter (where stock_actual > 0) as con_stock
from public.v_isaac_pos_productos_por_proveedor
group by 1
order by (coalesce(proveedor_nombre, 'SIN PROVEEDOR') = 'SIN PROVEEDOR'), count(*) desc;
