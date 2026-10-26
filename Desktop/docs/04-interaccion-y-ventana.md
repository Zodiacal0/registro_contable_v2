# 04 · Interacción, movimiento y ventana integrada

Tres mejoras sobre el rediseño: la gráfica y la tabla responden al usuario, la app tiene
movimiento sutil y la ventana usa una barra de título propia y oscura.

## Gráfica interactiva y filtros

- **Pasar el mouse por un día** resalta su columna y escribe arriba de la gráfica
  "Viernes 23 de octubre · 15.49" (como la mira de Binance). Reemplaza al tooltip.
- **Clic en un día** filtra la tabla a ese día; otro clic lo quita.
- **Clic en una categoría** de la leyenda filtra por ella: la elegida se marca y las demás
  se apagan.
- **Búsqueda** en Movimientos, sin importar mayúsculas ni tildes ("cafe" encuentra "Café").
- Los filtros activos aparecen como **píldoras con ✕**.

### Cómo está hecho

**Estado en el modelo, no en la vista.** `ModeloUi` tiene `diaFiltro`, `categoriaFiltro` y
`busqueda` como propiedades, y `movimientosFiltrados()` aplica los tres. La vista solo
escucha y pinta. Al cambiar de mes se limpia el filtro de día (un día pertenece a un mes).

**Repintado en dos niveles** (`VistaResumen`). Si cambian los datos, se repinta todo. Si
cambia un filtro, la selección o la búsqueda, se repinta **solo la tabla**. Es necesario: si
el campo de búsqueda se recreara en cada tecla, perdería el foco mientras escribes. Por eso
la sección de Movimientos se construye una vez y solo cambia su contenido.

**Binding bidireccional.**
`busqueda.textProperty().bindBidirectional(modelo.busquedaProperty())`: escribir actualiza el
modelo, y si el modelo limpia la búsqueda ("Quitar filtros") el campo se vacía solo.

**`Bindings.createStringBinding`** arma el texto sobre la gráfica a partir de dos
propiedades (día bajo el mouse y día filtrado): se recalcula cuando cambia cualquiera.

**Quitar tildes con `java.text.Normalizer`.** `NFD` separa "é" en "e" + acento combinado;
`replaceAll("\\p{M}", "")` borra las marcas (`\p{M}` = categoría Unicode "Mark").

## Movimiento

Todo en `Movimiento.java`, con una sola curva de salida y apagado si Windows tiene
**"reducir movimiento"** activado (`Platform.getPreferences().isReducedMotion()`).

- **Al cambiar de mes** el saldo cuenta hasta su valor y las barras crecen, al mismo tiempo:
  un solo momento, no efectos sueltos.
- **Al cambiar de vista**, entra con un fundido y un deslizamiento de 14 px.
- **Al registrar un gasto**, su fila destella y se apaga en 1.6 s, para que veas dónde quedó.
  `ModeloUi.tomarUltimoRegistrado()` devuelve el id y lo olvida, así el destello ocurre una
  sola vez aunque la tabla se repinte.

**Animar cualquier valor:** una `Transition` anónima con `interpolate(double fraccion)` recibe
de 0 a 1 en cada cuadro. Con eso se interpola el monto (`BigDecimal`) y el alto de las
barras, sin depender de `Timeline` y `KeyValue`.

## Carga tipo template (esqueleto) y entrada en cascada

Al abrir la app, cambiar de mes o cambiar de vista, las secciones entran **en cascada**:
cada una 40 ms después de la anterior, subiendo 10 px, desde el primer cuadro.

- **El esqueleto solo sale si hay espera real.** Al principio se mostraba siempre ~340 ms,
  pero SQLite responde en milisegundos: era una carga fingida que hacía sentir lenta la app.
  Ahora `Movimiento.esperar(esqueleto)` arranca un `PauseTransition` de 200 ms. Si los datos
  llegan antes (`listo()`), se cancela y el esqueleto nunca aparece. Solo si la carga pasa de
  200 ms se muestra, y se desvanece al llegar los datos. Por eso tiene sentido solo con cargas
  en segundo plano (como Analíticas): si el hilo de la interfaz está bloqueado, tampoco
  podría dibujarse.
- **El brillo se pinta desde Java** (`Esqueleto.java`): un `AnimationTimer` mueve en cada
  cuadro el centro de un `LinearGradient` y se lo asigna como `Background` a cada bloque.
  Reescribir un estilo CSS 60 veces por segundo sería más caro (cada cambio de estilo se
  vuelve a parsear).
## Por qué se sentía "a golpes" y qué cambió

- **La curva.** Todas las entradas usaban `ease-out-expo` (0.16, 1, 0.3, 1), que hace el
  60 % del recorrido en el primer 10 % del tiempo: eso se lee como un salto y luego un
  arrastre. Ahora `Movimiento.SALIDA` es una desaceleración estándar (0, 0, 0.2, 1) y
  `Movimiento.SUAVE` (0.4, 0, 0.2, 1) es ease-in-out, para lo que se abre o se mueve en su
  lugar (bloques desplegables, el panel, el scroll).
- **Bloques que se despliegan** (`Movimiento.desplegar`): "Se repite", "Va a una deuda o
  meta", "Personalizada" y los interruptores de fecha de Metas y Deudas ya no aparecen de
  golpe. El alto crece de 0 a su tamaño natural bajo un recorte, con fundido y un desliz de
  6 px. Dos trampas que resolvió: el `VBox` mete su espaciado apenas el bloque ocupa lugar
  (se compensa con un margen negativo animado), y hay que medir el bloque **después** de que
  los listeners le pongan su contenido, o el alto medido no es el final y salta al terminar.
- **El interruptor.** La perilla saltaba de lado por CSS (`-fx-alignment`). Ahora
  `Campos.preparar` la desliza con `translateX` y el fondo cambia con una transición CSS.
- **Transiciones CSS** (`transition`, JavaFX 23+): los colores de botones, chips, menú y
  filas se funden al pasar el mouse o seleccionar, en vez de cambiar al instante. Solo
  colores: tamaños y posiciones siempre se animan desde Java.
- **`managed` ligado a `visible`:** en JavaFX un nodo invisible **sigue ocupando espacio**.
  Sin ese binding, el esqueleto oculto estiraría el área de scroll.
- **Interfaz `Revelable`:** la ventana llama `revelar()` en la vista que entra sin saber cómo
  está armada. Es el mismo principio que el resto del proyecto: depender del "qué" y no del
  "cómo".

## Desplazamiento suave

JavaFX mueve el scroll a saltos con cada paso de la rueda. `DesplazamientoSuave` lo
reemplaza: la rueda solo mueve un **objetivo** y un `AnimationTimer` acerca la posición real
un 18% de la distancia restante en cada cuadro. Eso es una aproximación exponencial, que
arranca rápido y frena suave al llegar.

- Usa un **event filter** y no un handler: los filtros se ejecutan en la fase de captura,
  antes de que el `ScrollPane` procese el evento y salte.
- La rueda trae píxeles y `vvalue` va de 0 a 1, así que se convierte dividiendo entre la
  altura recorrible (contenido menos viewport).
- Si arrastras la barra a mano, el siguiente giro de rueda parte desde donde la dejaste.
- Se aplica a la vista de resumen, a las dos columnas de categorías y al panel.

## Ventana con barra de título propia

`StageStyle.EXTENDED` + `HeaderBar` (JavaFX 25): la app dibuja la barra de título y Windows
conserva arrastrar, redimensionar, el snap y sus botones. La barra comparte tono con el riel
y forman la "L" oscura del cliente de Steam.

**Es una función "preview".** Hay que activarla con `-Djavafx.enablePreview=true`, y su API
puede cambiar en próximas versiones de JavaFX. Al actualizar JavaFX, revisar esto primero.

**Trampa del lanzador (se descubrió con las capturas).** Ni poner la propiedad en `main()` ni
en un bloque `static` de `App` funcionó. Cuando la clase principal extiende `Application`, el
lanzador de Java (`LauncherHelper.FXHelper`) arranca JavaFX **sin llamar a `main()`** y sin
inicializar la clase hasta después del toolkit, que ya leyó la propiedad. Solución: la
propiedad va en la línea de comandos de la JVM (`ejecutar.cmd`, `demo.cmd`).
`App.activarPreview()` queda para los `main()` que sí corren primero (`Demo`).

**Botones de Windows visibles.** Por defecto el ícono es gris `#777`, casi invisible sobre la
barra. JavaFX los estiliza con clases internas (`.-FX-INTERNAL-close-button`...) en una hoja
pegada al propio botón, que le gana a la de la escena. Por eso nuestro CSS usa `!important`.
Cerrar se ilumina con el tono claro de la paleta en vez del rojo de Windows.

**La ventana cabe en la pantalla.** Con escalado de Windows al 125%, 860 px lógicos ya no
caben en 1080 px reales y la barra quedaba fuera. `mostrar()` limita el tamaño al 94% del
área visible (`Screen.getPrimary().getVisualBounds()`).

## Panel de captura que se oculta

El botón **»** del panel lo oculta y el contenido llega hasta el borde derecho. Para
reabrirlo aparece un círculo claro **+ Nuevo** en el riel izquierdo, debajo de la navegación
(como el "+ Agregar un juego" de la barra lateral de Steam). Es la única luz con el panel
cerrado y no mueve nada al aparecer, porque va debajo de los botones de navegación.
`Ctrl+N` o un clic en un gasto de la tabla también lo vuelven a abrir.

Se probaron antes dos lugares que no funcionaron, por si sirve el porqué:
- **En la barra de título:** quedaba apretado contra el borde y competía con los botones de Windows.
- **En un riel propio a la derecha:** dejaba una franja oscura casi vacía que desbalanceaba la ventana.

- **Animación:** el ancho del panel va de 360 a 0 con un `Rectangle` como *clip*, para que el
  contenido no se desborde mientras se angosta. Al terminar sale del `BorderPane`.
- **Se recuerda entre sesiones con `java.util.prefs.Preferences`**: un almacén clave-valor por
  usuario que trae Java (en Windows vive en el registro, `HKCU\Software\JavaSoft\Prefs`).
  Ideal para preferencias chicas; los datos de verdad siguen en SQLite.
- Si la ventana es baja, el panel se **desplaza** (`ScrollPane` con `fitToHeight`) en vez de
  cortar lo de abajo.

## Captura sin fricción

- El recuadro del monto se marca al escribir (`:focus-within`).
- El error del monto sale debajo del campo y le devuelve el foco.
- El monto acepta `Q 1,250.50`, `12,50` o `$1.250,75` (`FormatoTest`, 16 casos).
- La descripción es opcional.

## Verificación

- 29 pruebas JUnit.
- `CapturaPantallas` recorre 11 estados: resumen, tabla, edición, calendario, categorías,
  mes vacío, error de monto, gasto guardado, filtro por día, búsqueda y filtro por
  categoría.
- La ventana real se verificó con una captura de pantalla del escritorio (botones incluidos),
  porque el `snapshot` de JavaFX no dibuja los botones del sistema.
