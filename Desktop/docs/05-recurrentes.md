# 05 · Gastos recurrentes

Suscripciones, servicios y membresías que se cobran solos: Netflix cada mes, el dominio
cada año, el agua cada 2 meses.

## Cómo se usa

- **Crear:** en el panel de captura, activa **"Se repite"**, elige la frecuencia (Mensual,
  Anual o Personalizada: cada N días o meses) y la fecha del **primer cobro**.
- **Cobros automáticos:** cada vez que abres la app se registran como gastos normales los
  cobros que vencieron. En la tabla llevan el ícono ↻.
- **Vista "Recurrentes"** (en el riel): la lista con el costo total estimado (≈ al mes y ≈ al
  año) y, en el detalle de cada una, editar, pausar o reanudar, eliminar y ver los cobros
  registrados.
- **Resumen:** el bloque **"Próximos cobros"** muestra los siguientes 4 cobros de los
  próximos 30 días.

## El modelo

```
recurrencias (V5)                 gastos
─────────────────                 ──────
id, descripcion, monto            ...
cada + unidad (DIAS | MESES)      recurrencia_id  ──►  de qué recurrencia salió (NULL = manual)
inicio          (primer cobro)
generada_hasta  (hasta dónde ya se registró)
activa, actualizado_en, eliminado
```

Con `cada` + `unidad` caben todas las frecuencias: mensual = `1 MESES`, anual =
`12 MESES`, quincenal = `15 DIAS`. Es más simple que un enum con un caso por frecuencia, y
cualquier "cada N" sale gratis.

## Las tres decisiones importantes

**1. Las fechas se calculan siempre desde el inicio.** `cobro(k) = inicio.plusMonths(k * cada)`.
Si se sumara un mes al cobro *anterior*, un cobro del 31 caería en 28 en febrero y se quedaría
en 28 para siempre. `plusMonths` ajusta al último día del mes solo cuando hace falta, y como
cada fecha parte del inicio, marzo vuelve al 31. Lo verifica `RecurrenciaTest.mensualDesdeEl31NoSeQuedaEnEl28`.

**2. El id de cada cobro es determinístico.**
`UUID.nameUUIDFromBytes("recurrencia|fecha")` (UUID versión 3) produce siempre el mismo id
para el mismo cobro. Eso da dos garantías:
- **Idempotencia:** generar dos veces no duplica (`INSERT ... ON CONFLICT DO NOTHING`).
- **Sincronización:** cuando el celular y la PC generen "Netflix del 5 de noviembre" por su
  cuenta, los dos producirán el mismo id, y al sincronizar será un solo gasto.

**3. Lo que el usuario cambió se respeta.** Si editas o borras un cobro generado, la fila
sigue existiendo (borrado lógico), así que `DO NOTHING` no la pisa ni la revive. Pausar
detiene los cobros, y al reanudar se salta lo que venció en pausa. Editar una recurrencia
afecta solo a los cobros futuros.

`generada_hasta` es solo una optimización para no recorrer fechas viejas. La protección real
contra duplicados es el id determinístico.

## Otros detalles de Java que aparecen

- **`ON CONFLICT DO NOTHING` + `executeUpdate()`:** el número de filas afectadas (0 o 1) dice
  si el insert ocurrió; así `generarPendientes()` cuenta cuántos cobros nuevos registró.
- **Enum en la base por nombre** (`unidad.name()` / `Unidad.valueOf(...)`), con un `CHECK` en
  SQL que solo permite `'DIAS'` y `'MESES'`.
- **`record Servicios`** dentro de `App`: con dos servicios, `crearServicios()` devuelve un
  record que los agrupa. Es un caso típico de record como "tupla con nombre".
- **`Math.clamp`** (Java 21+) limita un valor a un rango en una línea.
- **Una sola señal de cambio:** `ModeloUi.recargar()` lee las recurrencias **antes** de
  publicar el resumen, así las vistas que escuchan el resumen ya encuentran todo actualizado.

## Pruebas

10 nuevas: 5 de dominio (`RecurrenciaTest`: fechas sin deriva, anual, cada 15 días, costo
mensual, id determinístico) y 5 de servicio (`RecurrenciaServicioTest`: cobros al crear, no
duplicar, no revivir lo borrado, pausa y reanudación, costo de las activas). Total: 39.

## Cambiar el primer cobro

Al editar una recurrente se puede elegir **"Primer cobro"**. Monto, frecuencia y categoría
solo cambian los cobros futuros. Mover el primer cobro, en cambio, **rehace el calendario**
(`RecurrenciaServicio.editar` con `inicio`):

1. Los cobros registrados que caen en el calendario nuevo se conservan, aunque los hayas
   editado.
2. Los que ya no caen se eliminan con borrado lógico.
3. Se registran los que falten desde el nuevo inicio hasta hoy.

Gracias a los ids determinísticos ("recurrencia|fecha"), regenerar nunca duplica. Detalle que
apareció en la prueba: el `UPDATE` del repositorio no incluía `inicio`, porque antes nunca
cambiaba. Lo cubre `RecurrenciaServicioTest.moverElPrimerCobroRehaceElHistorial`.
