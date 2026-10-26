# 08 · Presupuesto mensual

## Qué hace

Vista **Presupuesto** (riel, debajo de Resumen). Tú armas el plan y la app hace las cuentas:

1. **Sueldo al mes.** Lo escribes una vez. Vacío = se usa lo que registres como ingreso en
   Cuentas ese mes.
2. **Lo que normalmente gasto.** Una lista que armas tú: renta, luz, comida, transporte...
   Cada línea muestra qué **porcentaje del sueldo** se lleva. "Traer mis recurrentes" agrega
   de un clic las recurrencias activas que aún no estén en la lista (con su costo mensual).
3. **Disponible al mes** = sueldo − gastos del plan. Es la cifra grande. Si sale negativa, los
   gastos no caben en el sueldo.
4. **Lo disponible lo asigno a.** Otra lista: ahorro, fondo para imprevistos, salidas... El
   botón "El resto" rellena el monto con lo que todavía no tiene destino, hasta que diga
   "Todo asignado".

La barra "Así se va el sueldo" pinta un tramo por línea (gastos en violeta medio,
asignaciones en lila claro) y deja en vacío lo que sigue sin asignar. Debajo, para comparar
el plan con la realidad: **"Gastado de verdad este mes"** y qué porcentaje del sueldo es.

## Por quincena

El interruptor **"Me pagan por quincena"** (ajuste `pago_quincenal`) activa dos tarjetas,
"1ª quincena" y "2ª quincena", cada una con la mitad del sueldo, sus gastos, lo asignado y lo
disponible. Cada línea del plan gana un selector **1ª | 2ª | Mitad y mitad** (columna
`quincena`, migración V9): la renta cae en la primera, la tarjeta en la segunda, la comida
se parte. `LineaPlan.en(quincena)` devuelve cuánto de la línea cae en cada una, y `PlanMes`
suma por quincena con los mismos métodos que para el mes (`gastosEn`, `disponibleEn`,
`sinAsignarEn`). El total del mes no cambia: es la suma de las dos.

La vista queda en tres niveles: arriba el **mes completo**; en medio **"Cómo va el mes"**,
la quincena en curso en grande (la que toca hoy; un selector 1ª | 2ª muestra la otra) con lo
disponible, las líneas que caen en ella y **lo gastado de verdad** en esos días (del 1 al 15
es la primera, del 16 en adelante la segunda); y abajo **las dos quincenas** lado a lado.

Simplificación: el sueldo se parte exactamente a la mitad. Si tus dos pagos son distintos,
se puede agregar un ajuste con el monto de cada uno.

## Modelo

Una sola tabla, `plan_lineas`: nombre, monto, `grupo` (GASTO o ASIGNACION), orden. No hay
categorías ni fechas: el plan es tuyo, en tus palabras. Los montos se editan en la misma fila
(Enter o clic afuera) y la ✕ quita la línea (borrado lógico, como todo lo demás).

`PlanMes` es un record inmutable con los totales como métodos (`totalGastos`,
`disponible`, `totalAsignado`, `sinAsignar`, `parte`): lo derivado se calcula, no se guarda.

## Decisiones

- **El plan es el mismo todos los meses.** Es lo que "normalmente" gastas; si cambia, lo
  editas. Cambiar de mes solo cambia el sueldo registrado y lo gastado de verdad.
- **Las líneas no se atan a categorías ni recurrencias.** Lo probé primero (topes por
  categoría, recibos y cuotas calculados solos) y resultaba rígido: el usuario quería escribir
  su lista. "Traer recurrentes" es solo un atajo para no teclearlas; después son líneas como
  cualquier otra (se comparan por nombre, sin tildes ni mayúsculas, para no duplicar).
- **El sueldo es un ajuste** (`ajustes.ingreso_mensual`), no una tabla: es un solo valor.
- Los campos guardan **solo si el valor cambió** (`guardarAlSalir`): si no, cada clic fuera
  del campo recargaría toda la vista.

## Pruebas

`PresupuestoTest` (5): sueldo fijado o registrado, porcentaje y disponible, asignar hasta
cero (editar y quitar), traer recurrentes sin duplicar y con el costo mensual, y lo gastado
real. Total: 65.
