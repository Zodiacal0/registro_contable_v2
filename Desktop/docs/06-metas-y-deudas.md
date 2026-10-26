# 06 · Metas de ahorro y deudas

## Qué hace

**Metas** (en el riel): objetivos de ahorro con fecha opcional. Les haces aportes o retiros, y
la app muestra el progreso, cuánto falta y **cuánto aportar al mes** para llegar a tiempo.
Los aportes **no son gastos**: es dinero que apartas, no que se va.

**Deudas** (en el riel), en dos pestañas:
- **Debo** (tarjeta, préstamo, algo que le debes a alguien): cada abono **es un gasto**.
  Aparece en Movimientos (con el ícono de tarjeta) y baja el saldo.
- **Me deben:** lo que te pagan baja el saldo, pero no es un gasto.

## El modelo (V6)

```
metas ──< aportes            (positivo = guardar, negativo = retirar)
deudas ──< cobros_deuda      (solo ME_DEBEN: lo que me pagan)
deudas ──< gastos.deuda_id   (solo DEBO: el abono ES un gasto)
```

**Por qué el abono de lo que debo es un gasto y no una fila aparte.** Si fueran dos
registros (un "abono" y un "gasto" gemelo), podrían desincronizarse: borras el gasto en
Movimientos y la deuda sigue creyendo que pagaste. Con **una sola fuente de verdad** (el
gasto lleva `deuda_id`), lo pagado se calcula sumando esos gastos. Borrar el gasto devuelve
el saldo solo, y lo verifica `MetasYDeudasTest.borrarElGastoDelAbonoDevuelveElSaldo`.

**Nada derivado se guarda.** Ni lo ahorrado, ni el saldo, ni el porcentaje: `EstadoMeta` y
`EstadoDeuda` son records que reciben la suma y **calculan** lo demás en sus métodos
(`restante()`, `saldo()`, `aporteMensualSugerido()`). Lo que no se guarda no puede quedar
desactualizado.

## Diseño de código que vale la pena ver

**`Montos`**: la validación "mayor que cero y máximo 2 decimales" estaba copiada en `Gasto` y
`Recurrencia`. Ahora la usan todos los records (también `Meta`, `Deuda` y `Abono`) desde un
solo lugar. `textoObligatorio` recibe el mensaje **completo** porque el género cambia ("La
descripción no puede estar vacía", "El nombre no puede estar vacío").

**`Abono`**: aportes y cobros tienen la misma forma, así que comparten un record y un
repositorio. `AbonoRepositorio` se **parametriza con la tabla** y se crea con fábricas
(`AbonoRepositorio.aportes(bd)`, `AbonoRepositorio.cobrosDeuda(bd)`). Meter el nombre de la
tabla en el SQL es seguro porque viene de una constante del código, nunca de lo que escribe
el usuario. Esa es la diferencia con una inyección SQL.

**`VistaListaDetalle` (clase abstracta)**: Metas y Deudas comparten la estructura de
biblioteca (lista, detalle, esqueleto de carga, scroll suave) y solo implementan `pintar()`.
Una trampa de Java documentada en la clase: **el constructor de la superclase corre antes de
que se inicialicen los campos de la subclase**. Si la base llamara a `pintar()`, la subclase
pintaría con sus campos todavía en `null`. Por eso cada subclase llama a `pintar()` al final
de su propio constructor.

**`Servicios` (record)**: con cuatro servicios, el record pasó al paquete `servicio` y
`ModeloUi` lo recibe completo, en vez de un constructor que crece con cada función nueva.

**Un enum privado para el modo de la vista** (`VER`, `CREAR`, `EDITAR`): el detalle muestra
una cosa u otra según el modo, sin varios booleanos que podrían contradecirse.

## Abonar o aportar desde el panel "Nuevo gasto"

Para no ir a Deudas o Metas cada vez, el panel tiene una caja **"Va a una deuda o meta"**
(debajo de "Se repite"). Al activarla aparecen como chips las deudas que debo con saldo
(ícono de tarjeta) y las metas sin completar (bandera):

- **Deuda:** se registra un abono, que es un gasto. La descripción es opcional; si la dejas
  vacía queda "Abono · nombre" (el campo lo sugiere como texto de ayuda). La categoría sí
  aplica. Si el monto supera el saldo, el error sale junto al monto.
- **Meta:** se registra un aporte, que no es gasto. Por eso descripción y categoría se
  desactivan: no se guardan en ningún lado.
- El botón dice qué va a pasar ("Registrar abono", "Aportar a la meta"). Al guardar, la caja
  se apaga sola y aparece una confirmación con el saldo o lo que falta. La confirmación hace
  falta porque un aporte no aparece en Movimientos y si no, no habría señal de que se guardó.
- "Se repite" y el destino se excluyen: un cobro recurrente no abona ni aporta (por ahora).
- Las dos cajas con interruptor comparten `filaInterruptor(...)` en `PanelGasto`, y el CSS
  pasó de `caja-repetir` a `caja-interruptor`.

**Detalle de JavaFX:** una `Label` con `setWrapText(true)` dentro de un `VBox` puede salir
cortada con "…" igual. Si el VBox no tiene espacio, la comprime hasta su altura mínima, que
por defecto es una línea. `setMinHeight(Region.USE_PREF_SIZE)` le pide que nunca baje de su
altura preferida (la del texto completo).

## Pruebas

9 en `MetasYDeudasTest` (la novena: descripción propia o por defecto del abono):
- **Metas:** progreso con aportes y retiros, aporte mensual sugerido, que los aportes no son
  gastos, que no se puede retirar de más y meta completada.
- **Deudas:** abono como gasto, borrar ese gasto devuelve el saldo, que lo que me deben no
  genera gastos, que no se puede abonar más que el saldo y deuda saldada.

Total: 48.

## Pendiente a propósito

- **Intereses** de tarjetas o préstamos: no se calculan. El monto es el total que debes; si
  crece, edítalo.
- **Metas en el Resumen:** por ahora viven en su propia vista. Se puede sumar un bloque como
  "Próximos cobros" si te resulta útil.
