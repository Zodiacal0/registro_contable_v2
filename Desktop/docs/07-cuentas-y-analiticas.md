# 07 · Cuentas, monedas y analíticas

## Cuentas

Una **cuenta** es de dónde sale (o adónde entra) el dinero: efectivo, banco, tarjeta. Cada
gasto sale de una cuenta; en el panel "Nuevo gasto" aparece **"Sale de"** con una opción por
cuenta (solo si tienes más de una). El panel recuerda la última cuenta que usaste.

En la vista **Cuentas** (riel) ves el saldo de cada una y el total. Desde ahí puedes:
- **Registrar ingresos** (salario, ventas).
- **Transferir** entre cuentas. No es gasto ni ingreso, solo cambia dónde está el dinero.
- Ver el **historial**: gastos, ingresos y transferencias juntos. Desde aquí se quitan
  ingresos y transferencias; los gastos se quitan desde Movimientos.

**El saldo no se guarda; se calcula** (`CuentaServicio.saldo`):

```
saldo = saldo inicial + ingresos − gastos + transferencias que entran − transferencias que salen
```

Mismo principio que en metas y deudas: lo que no se guarda no puede desincronizarse.

### La cuenta principal (id fijo)

La migración V7 crea "Efectivo" en quetzales con un id **fijo**
(`00000000-0000-4000-8000-000000000001`) y le asigna todos los gastos y recurrencias que ya
existían. Si cada equipo generara un UUID al azar, al sincronizar habría dos "Efectivo". Con
el mismo id en todos los equipos, la sincronización ve una sola cuenta. La principal no se
puede eliminar.

## Monedas

Cada cuenta tiene **una moneda** (quetzales o dólares), y el monto de un gasto está en la
moneda de su cuenta. Los **totales** (resumen, categorías, analíticas, patrimonio) se suman
en quetzales, convirtiendo con el tipo de cambio que defines en Cuentas ("1 US$ = Q 7.70").

- **`Conversor` (record inmutable):** una foto de "qué moneda tiene cada cuenta y a cuánto
  se cambia". `ResumenMes` guarda la foto con que se calculó, así sus números nunca se
  mezclan con un tipo de cambio que cambió después.
- **`Supplier<Conversor>` en `GastoServicio`:** el servicio no guarda una foto, guarda
  *cómo pedirla* (`cuentas::conversor`). Cada resumen pide la del momento.
- **Simplificación consciente:** se usa el tipo de cambio actual también para gastos viejos.
  Lo exacto sería guardar el cambio del día en cada gasto; para una app personal no vale la
  complejidad.
- **La moneda no se cambia después de crear la cuenta.** Si se pudiera, 100 quetzales ya
  registrados pasarían a ser 100 dólares. Por eso `CuentaRepositorio.guardar` no la incluye
  en el `UPDATE`.
- En pantalla, la moneda principal va sin símbolo (es la de todos los totales) y las demás
  lo llevan: `US$ 12.99`. En la vista Cuentas, donde conviven, todas llevan símbolo.

## Analíticas

Vista **Analíticas** (riel). Analiza los **últimos 90 días**, sin abonos a deudas, todo
calculado en el equipo. Cada regla es simple a propósito, para que cada número se pueda
explicar:

| Sección | Regla |
|---|---|
| Lo que más repites | Misma descripción (sin importar mayúsculas ni tildes) 3 o más veces. Sin recurrentes. |
| Gastos hormiga | Compras de Q50 o menos que no son recurrentes. Cuántas, cuánto al mes y qué % de tu gasto. |
| Subieron | Por categoría: últimos 30 días frente al promedio mensual de los 90 anteriores. Sube si pasa +20 % y +Q20. |
| Recurrentes | Costo al mes y al año de cada una (convertido) y qué % de tu gasto mensual se va ahí. |
| Podrías ahorrar | Suma de ideas concretas, y en cuántos meses llegarías a tu meta pendiente más avanzada. |

**El total de ahorro no cuenta dos veces lo mismo.** Las ideas de "reducir X a la mitad"
toman los 3 gastos más repetidos. La de gastos hormiga se calcula **sin** esos gastos (un
café es frecuente y también chico). Las ideas de categorías que subieron y de recurrentes se
muestran como "Aparte del total", porque se cruzan con las otras.

**Diseño para probar:** `AnaliticaServicio.analizar(...)` es **estático y puro**. Recibe
listas y devuelve un `Analisis`, sin tocar la base. `AnaliticaTest` le pasa gastos armados a
mano. El método de instancia `analizar()` solo lee los datos y lo llama.

**Corre en otro hilo.** Recorre 120 días de gastos, así que `VistaAnaliticas` usa
`CompletableFuture.supplyAsync(modelo::analizar)` y vuelve al hilo de JavaFX con
`Platform.runLater`. Dos detalles:
- Un contador `pedido` descarta resultados viejos: si cambian los datos mientras se calcula,
  solo se pinta el último pedido.
- El esqueleto de carga solo aparece si el cálculo tarda más de 200 ms. Ver
  [04](04-interaccion-y-ventana.md).

### Gráficas y comparativos

Después del número de ahorro hay tres comparativos:

- **Ritmo del mes:** el gasto acumulado día a día de este mes contra el mes pasado, y cuánto
  más o menos llevas que el mes pasado a esta altura.
- **Ingresos y gastos:** los últimos 6 meses en el mismo eje, porque los dos están en
  quetzales. Nunca se usan dos escalas en una gráfica, porque engañan. Aquí los gastos sí
  incluyen abonos a deudas: también es dinero que sale.
- **Por categoría:** gráfica tipo *bullet*. La barra son los últimos 30 días y la marca
  vertical, tu promedio mensual; la barra que pasa la marca subió (lleva el chip ▲). Reemplaza
  la lista "Subieron".

`GraficaCurva` es un componente propio (un `Region` que dibuja con `Path`):

- **Curvas monótonas** (Fritsch–Carlson, la misma de `d3.curveMonotoneX`): en cada punto, la
  tangente es 0 si la serie cambia de dirección, y nunca más empinada de lo que permiten los
  tramos vecinos. Una curva suave cualquiera "rebota" entre puntos y, con dinero, dibujaría
  montos que nunca existieron (por ejemplo, bajo cero).
- **Dos series que se distinguen sin color:** la principal es continua (#E2DDE7) y con área;
  la de comparación es discontinua (#8A7AA8). Las validé con el script de la guía de
  visualización contra el fondo #17151C: ΔE 30 también para daltonismo y contraste mayor a
  3:1. Los avisos de "poco croma" son esperables con una paleta gris violácea a propósito, y
  por eso el trazo y las etiquetas cargan con la diferencia.
- **Etiquetas directas** con el valor final a la derecha de cada línea, que se separan solas
  si se enciman. Una guía vertical al pasar el mouse muestra los valores de ese punto.
- **Techo "redondo" del eje:** 1, 2, 2.5 o 5 × 10ⁿ, para que las marcas caigan en números
  legibles.
- Solo se redibuja cuando cambia el tamaño. Las etiquetas reciben `applyCss()` al crearse,
  para que el primer cuadro no salga sin estilo.

## Persistencia: `Consultas`

Los repositorios repetían el mismo bloque JDBC (abrir, preparar, poner parámetros, recorrer
filas, cerrar). Los nuevos (`CuentaRepositorio`, `IngresoRepositorio`,
`TransferenciaRepositorio`, `AjustesRepositorio`) usan `Consultas`, que lo escribe una vez.

`Consultas.Mapeador<T>` es una interfaz funcional propia porque
`java.util.function.Function` **no puede lanzar `SQLException`**, que es una excepción
verificada. Su firma tiene que declararla, y así `rs -> rs.getString("valor")` compila.

## Pruebas

- `CuentasTest` (5): cuenta principal creada por la migración, saldo con ingresos, gastos y
  transferencias, transferencia entre monedas, resumen que convierte dólares, y la principal
  no se puede eliminar.
- `AnaliticaTest` (6): agrupación sin tildes ni mayúsculas, umbral de hormiga (sin
  recurrentes ni deudas), categoría que subió, ahorro sin contar doble, meses hasta la meta y
  conversión de dólares antes de analizar.

Total: 60 (con la de recurrentes: mover el primer cobro).

## Pendiente a propósito

- Las **recurrentes** existentes no cambian de cuenta desde su vista (las nuevas toman la
  cuenta elegida en el panel).
- Lo que **me deben**, cuando me pagan, no entra a ninguna cuenta. Si te sirve, se puede
  elegir la cuenta como en los ingresos.
- El umbral de gasto hormiga (Q50) es fijo; se puede volver ajuste.
