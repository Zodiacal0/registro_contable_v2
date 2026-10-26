# 02 · Interfaz en JavaFX

Fase 2: la consola temporal se reemplaza por una UI en JavaFX 25 con la línea gráfica
del proyecto. El dominio y la persistencia apenas cambiaron: esa era la idea de las capas.

> Nota posterior: el aspecto visual de esta fase (colores azul/amarillo, Inter, hoja modal)
> se reemplazó en la fase 3 (`03-rediseno.md`). La arquitectura descrita aquí (ModeloUi,
> ResumenMes, hilos, CSS, module path) sigue vigente.

## Qué se agregó

```
src/main/java/com/registrogastos/
├── App.java                 ahora extiende javafx.application.Application
├── servicio/
│   ├── ResumenMes.java      foto inmutable del mes, ya calculada
│   └── TotalCategoria.java
└── ui/
    ├── ModeloUi.java        estado observable (puente UI ↔ servicio)
    ├── VentanaPrincipal.java  marco: barra lateral + vista + capa modal
    ├── VistaResumen.java    saldo, por categoría, movimientos
    ├── VistaCategorias.java alta / renombrar / eliminar
    ├── FormularioGasto.java hoja modal de crear/editar
    ├── Formato.java         montos, fechas y porcentajes en español
    ├── Paleta.java, Nodos.java  utilidades pequeñas
src/main/resources/
├── ui/estilos.css           TODA la línea gráfica
├── ui/fuentes/              Inter + IBM Plex Mono (OFL)
└── db/migraciones/V2__color_categoria.sql
src/test/java/com/registrogastos/CapturaPantallas.java   herramienta de desarrollo
```

Se eliminó `consola/MenuConsola.java`: cumplió su papel de probar el núcleo sin UI.

## Por qué JavaFX (y por qué la 25)

Swing sigue funcionando, pero estilizarlo es pelear con *Look and Feels*. JavaFX trae
**CSS**, **propiedades observables y bindings**, animaciones y un grafo de escena
moderno. Para una línea gráfica propia es la opción natural en Java de escritorio.

JavaFX se distribuye aparte del JDK desde Java 11. La **25.0.5** acompaña al JDK 25
LTS (la 27 existe, pero no es LTS).

## Classpath vs module path

JavaFX **exige cargarse como módulos con nombre**. Si lo pones en el classpath junto a una
clase `main` que extiende `Application`, falla con *"JavaFX runtime components are
missing"*. Por eso `ejecutar.cmd` hace:

```
--module-path target\javafx --add-modules javafx.controls     ← JavaFX como módulos
-cp target\classes;target\dependency\*                         ← la app y SQLite, en el classpath
```

y el `pom.xml` copia las dependencias en dos carpetas (`includeGroupIds` /
`excludeGroupIds` en `maven-dependency-plugin`). La alternativa sería hacer **modular**
la app entera (`module-info.java`). Es buen tema para más adelante, cuando se empaquete
con `jlink`/`jpackage`. Por ahora agrega complejidad sin beneficio.

`--enable-native-access=javafx.graphics,ALL-UNNAMED`: JavaFX también carga código nativo
(el renderizador Prism, Direct3D en Windows), igual que SQLite.

## Arquitectura de la UI: un modelo observable + una foto inmutable

```
clic "Guardar" → ModeloUi.registrar() → GastoServicio → SQLite
                       ↓
               recargar(): resumen.set(servicio.resumen(mes))
                       ↓
     listeners de resumenProperty() → VistaResumen.pintar(), VistaCategorias.pintar()
```

- **`ResumenMes`** es un record con todo lo que la pantalla necesita ya calculado
  (total, variación, promedio, desglose). La UI no suma ni agrupa: solo pinta. Ventaja:
  la lógica se prueba con JUnit sin abrir ventanas (ver `GastoServicioTest`).
- **Copias defensivas** en su constructor compacto (`gastos = List.copyOf(gastos)`):
  un record es inmutable, pero sus listas no lo son si te pasan una `ArrayList`.
  `List.copyOf` devuelve una lista inmodificable.
- **`ReadOnlyObjectWrapper`**: `ModeloUi` puede escribir el resumen, y hacia afuera solo
  expone `getReadOnlyProperty()`. Las vistas pueden escuchar pero no modificar.
- **Las vistas se repintan completas** con cada cambio. Es simple y sobra para cientos de
  filas. Con miles se cambiaría la lista de movimientos por un `ListView` (que solo crea
  nodos para las filas visibles).

## Hilos

JavaFX tiene **un hilo de UI** (*JavaFX Application Thread*) y todo lo que toca nodos
debe ocurrir ahí. Ciclo de vida: `launch()` → `init()` (otro hilo, sin UI: ahí se abre
y migra la base) → `start()` (hilo de UI: ahí se arma la ventana).

Las consultas a SQLite corren en el hilo de UI porque tardan microsegundos (archivo
local). Cuando llegue la **sincronización por red** eso no sirve: se usará un
`javafx.concurrent.Task` en otro hilo y `Platform.runLater` para volver a la UI.

## UI en código + CSS (sin FXML)

FXML separa la estructura en XML, pero agrega reflexión, `@FXML` y archivos que el
compilador no verifica. Con la UI en Java, un rename del IDE actualiza todo y los
errores aparecen al compilar. La separación que importa, **estructura vs aspecto**, la
da el CSS: en Java solo hay `getStyleClass().add("tarjeta")`, y colores, radios y
tipografías viven en `estilos.css`.

## Detalles de JavaFX que vale la pena entender

**Looked-up colors.** `-c-acento: #f0b90b;` en `.root` funciona como variable de CSS y la
usan todos los descendientes. Las variables de Modena (`-fx-base`, `-fx-accent`,
`-fx-focus-color`) se redefinen para que los controles nativos combinen con el tema.

**Fondos en capas.** `-fx-background-color: borde, relleno; -fx-background-insets: 0, 1;`
dibuja dos rectángulos: el segundo, 1 px más adentro, deja ver el primero como borde.
Se usa para bordes finos y anillos de foco **sin cambiar el tamaño del nodo** (un
`-fx-border` sí lo cambia y hace "saltar" el layout).

**`:focus-visible`** (JavaFX 19+). El anillo de foco aparece al navegar con teclado y no
al hacer clic.

**Fuentes con un peso por familia.** Al cargarlas, JavaFX registra
"Inter SemiBold" e "IBM Plex Mono Medium" como **familias distintas**, y
`-fx-font-weight: 600` no las encuentra. Por eso en el CSS se pide el peso por nombre
de familia. Se descubrió con las capturas: los títulos salían regulares.

**Bindings.** Las barras de "Por categoría" no calculan píxeles a mano:

```java
relleno.maxWidthProperty().bind(pista.widthProperty().multiply(total.proporcion()));
```

Si la ventana cambia de tamaño, la barra se ajusta sola.

**`ToggleGroup` siempre con uno seleccionado.** Por defecto, un clic sobre el botón ya
seleccionado lo deselecciona. Un listener que lo vuelve a seleccionar lo impide (se usa
en la navegación y en los chips de categoría).

**Hoja modal propia en vez de `Dialog`.** Un `Dialog` abre otra ventana del sistema, con
su barra de título nativa. Aquí la raíz es un `StackPane`: el `BorderPane` de la app
abajo y un "velo" encima con la hoja centrada. Así se controla el estilo y la animación
de entrada (fade + scale de 96% a 100%). Esc o un clic en el velo la cierran.

## Migración V2: el color de la categoría

En la primera versión el color salía de `id.hashCode() % 8`, y las capturas mostraron
el problema: con 5 categorías, dos salían del mismo color (es la paradoja del
cumpleaños). La solución correcta es **guardar el color**:

- `V2__color_categoria.sql`: `ALTER TABLE categorias ADD COLUMN color TEXT NOT NULL DEFAULT '#66c0f4'`.
  **V1 no se tocó**: una base que ya estaba en V1 recibe solo la V2.
- `GastoServicio.siguienteColor()` elige el color de la paleta **menos usado** por las
  categorías activas.
- El color viaja con la sincronización, así que el celular mostrará los mismos colores.

## `CapturaPantallas` (herramienta de desarrollo)

Vive en `src/test` para no viajar con la app. Crea una base temporal con datos de
ejemplo, abre la UI y guarda PNGs de cada pantalla con `Node.snapshot()`. Así se
revisa el diseño sin tocar tus datos. Las imágenes de este documento salieron de ahí.

```
mvnw.cmd test-compile
java --module-path target\javafx --add-modules javafx.controls ^
     -cp "target\classes;target\test-classes;target\dependency\*" ^
     com.registrogastos.CapturaPantallas C:\ruta\salida
```

## Uso

```
cd Desktop
mvnw.cmd test        13 pruebas
ejecutar.cmd         abre la app (Ctrl+N = nuevo gasto, Esc = cerrar hoja)
```
