---
name: Registro de Gastos
description: Escritorio financiero personal en gris violáceo; registrar y entender el mes sin cambiar de pantalla.
colors:
  carbon-violeta: "#17151c"
  pizarra-profunda: "#292531"
  ciruela-humo: "#45404f"
  malva-ceniza: "#716879"
  lila-niebla: "#aaa2b3"
  lavanda-hueso: "#e2dde7"
  riel-noche: "#121016"
  panel-elevado: "#211e28"
  superficie-tabla: "#1f1c26"
  texto-terciario: "#8e8597"
  linea: "rgba(226, 221, 231, 0.07)"
  velo-hover: "rgba(226, 221, 231, 0.045)"
  lavanda-hueso-hover: "#f1edf4"
  lavanda-hueso-pressed: "#cdc6d4"
  categoria-lavanda: "#cbbfe0"
  categoria-violeta: "#8a7aa8"
  categoria-malva: "#d9a9c4"
  categoria-ciruela-oscura: "#6f6488"
  categoria-pervinca: "#a6b0d9"
  categoria-ciruela: "#b38aa6"
  sin-categoria: "#5e5669"
typography:
  cifra-hero:
    fontFamily: "Figtree Light SemiBold"
    fontSize: "64px"
    fontWeight: 600
    lineHeight: 1
  cifra-detalle:
    fontFamily: "Figtree Light SemiBold"
    fontSize: "44px"
    fontWeight: 600
  cifra-campo:
    fontFamily: "Figtree Light SemiBold"
    fontSize: "42px"
    fontWeight: 600
  titulo:
    fontFamily: "Figtree Light SemiBold"
    fontSize: "34px"
    fontWeight: 600
  titulo-panel:
    fontFamily: "Figtree Light SemiBold"
    fontSize: "20px"
    fontWeight: 600
  titulo-seccion:
    fontFamily: "Figtree Light SemiBold"
    fontSize: "17px"
    fontWeight: 600
  body:
    fontFamily: "Figtree Light"
    fontSize: "14px"
    fontWeight: 400
  body-medium:
    fontFamily: "Figtree Light Medium"
    fontSize: "14px"
    fontWeight: 500
  label:
    fontFamily: "Figtree Light Medium"
    fontSize: "12px"
    fontWeight: 500
  detalle:
    fontFamily: "Figtree Light"
    fontSize: "12px"
    fontWeight: 400
  micro:
    fontFamily: "Figtree Light"
    fontSize: "11px"
    fontWeight: 400
  cifra-tabla:
    fontFamily: "IBM Plex Mono Medium"
    fontSize: "15px"
    fontWeight: 500
  cifra-chica:
    fontFamily: "IBM Plex Mono Medium"
    fontSize: "12px"
    fontWeight: 500
  cifra-resumen:
    fontFamily: "IBM Plex Mono SemiBold"
    fontSize: "18px"
    fontWeight: 600
  etiqueta:
    fontFamily: "Figtree Light"
    fontSize: "13px"
    fontWeight: 400
  titulo-vacio:
    fontFamily: "Figtree Light SemiBold"
    fontSize: "16px"
    fontWeight: 600
  nano:
    fontFamily: "Figtree Light"
    fontSize: "10px"
    fontWeight: 400
rounded:
  barra: "3px"
  tramo: "4px"
  tooltip: "8px"
  dia: "9px"
  campo: "12px"
  tarjeta: "14px"
  popup: "16px"
  contenedor: "18px"
  pildora: "999px"
spacing:
  xxs: "2px"
  xs: "4px"
  sm: "8px"
  md: "14px"
  lg: "20px"
  xl: "28px"
  seccion: "40px"
  hero: "56px"
  riel: "84px"
  lista: "320px"
  panel: "360px"
components:
  button-primary:
    backgroundColor: "{colors.lavanda-hueso}"
    textColor: "{colors.carbon-violeta}"
    typography: "{typography.body}"
    rounded: "{rounded.pildora}"
    padding: "14px 20px"
  button-primary-hover:
    backgroundColor: "{colors.lavanda-hueso-hover}"
  button-primary-pressed:
    backgroundColor: "{colors.lavanda-hueso-pressed}"
  button-destructive:
    backgroundColor: "transparent"
    textColor: "{colors.lila-niebla}"
    rounded: "{rounded.pildora}"
    padding: "14px 20px"
  button-destructive-confirm:
    backgroundColor: "{colors.ciruela-humo}"
    textColor: "{colors.lavanda-hueso}"
  button-icon:
    backgroundColor: "rgba(226, 221, 231, 0.07)"
    rounded: "{rounded.pildora}"
    size: "38px"
  button-icon-hover:
    backgroundColor: "rgba(226, 221, 231, 0.13)"
  button-text:
    backgroundColor: "transparent"
    textColor: "{colors.lila-niebla}"
    rounded: "{rounded.tooltip}"
    padding: "6px 8px"
  input:
    backgroundColor: "{colors.carbon-violeta}"
    textColor: "{colors.lavanda-hueso}"
    rounded: "{rounded.campo}"
    padding: "12px 14px"
  chip:
    backgroundColor: "rgba(226, 221, 231, 0.06)"
    textColor: "{colors.lila-niebla}"
    rounded: "{rounded.pildora}"
    padding: "7px 13px 7px 11px"
  chip-selected:
    backgroundColor: "{colors.ciruela-humo}"
    textColor: "{colors.lavanda-hueso}"
  nav-item:
    backgroundColor: "transparent"
    textColor: "{colors.texto-terciario}"
    rounded: "{rounded.tarjeta}"
    width: "64px"
  nav-item-selected:
    backgroundColor: "rgba(226, 221, 231, 0.08)"
    textColor: "{colors.lavanda-hueso}"
  table:
    backgroundColor: "{colors.superficie-tabla}"
    rounded: "{rounded.contenedor}"
    padding: "6px 8px 8px 8px"
  capture-panel:
    backgroundColor: "{colors.panel-elevado}"
    width: "{spacing.panel}"
    padding: "34px 28px 22px 28px"
---

# Design System: Registro de Gastos

> Plataforma: aplicación de escritorio JavaFX 25 con JavaFX CSS (`Desktop/src/main/resources/ui/estilos.css`). Los tokens se registran en forma portátil (hex, px, roles) para que la futura app React Native (`Mobile/`) los reutilice; entre paréntesis va el nombre JavaFX cuando existe (`-c-*` son looked-up colors definidos en `.root`).

## Overview

**Creative North Star: "El escritorio violáceo de tres zonas"**

Un escritorio financiero en tres zonas fijas: riel de navegación, el mes en el centro y un panel de captura siempre abierto a la derecha. Registrar y entender conviven sin modales: el usuario ve cuánto lleva gastado, si va peor o mejor que el mes pasado y en qué día se le fue, y anota el siguiente gasto sin cambiar de pantalla. La composición toma de Apple la sobriedad y la píldora clara, de Binance la lectura de cifras tipo wallet y la tabla de movimientos, y de Steam los paneles en capas y la topología lista + detalle.

Todo vive dentro de una sola familia de seis grises violáceos fijada por el usuario, apilados en capas de oscuro a claro. No hay color semántico: la información se codifica por luminosidad, forma (triángulos de alza/baja) y posición, nunca por rojo/verde. La profundidad viene de capas tonales, una luz difusa detrás del hero y una única sombra estructural en el panel de captura. Densidad media: ni vacía ni recargada; se siente app de escritorio, no página web.

**Key Characteristics:**
- Paleta cerrada de seis tonos gris violáceo más derivados mezclados de la misma familia.
- Una sola superficie clara rellena por zona: la acción principal.
- Cifras tabulares en IBM Plex Mono; interfaz en Figtree.
- Bordes de 1 px como capa de fondo translúcida (`-c-linea`), no como `border`.
- Íconos propios como trazos SVG monocromos que heredan el color del texto.
- Layout fijo de tres zonas: 84 px · flexible · 360 px.

## Colors

Una escalera tonal gris violácea, de carbón casi negro a lavanda hueso, sin ningún matiz fuera de esa familia.

### Primary
- **Lavanda Hueso** (`-c-5`): el tono más claro. Color del texto principal y, como relleno, solo de la acción principal (píldora "Registrar gasto", círculo "+" de nueva categoría), del día seleccionado en el calendario y de la barra de hoy en la gráfica. Hover y pressed se aclaran/oscurecen dentro del mismo tono (`lavanda-hueso-hover`, `lavanda-hueso-pressed`).

### Secondary
- **Lila Niebla** (`-c-4`): texto secundario (subtítulos, etiquetas, celdas), barra del día pico, anillos de foco, borde del chip seleccionado y del campo enfocado. Es el "acento" de estado: foco y énfasis medio.

### Tertiary
- **Tintes de categoría** (`Categoria.PALETA`, guardados en la base y sincronizados): lavanda, violeta, malva, ciruela oscura, pervinca, ciruela. Se separan por luminosidad y un giro leve de tono, siempre dentro de la familia. Se usan solo como muestra/punto de 8-10 px, tramo de barra segmentada y punto en chips/filas. Ninguno es `#E2DDE7`. **Sin categoría** (`Paleta.SIN_CATEGORIA`) es más oscuro que cualquiera para no competir.

### Neutral
- **Carbón Violeta** (`-c-0`): fondo raíz, interior de campos, caja de monto y tarjeta "Hoy" (campos hundidos sobre el panel).
- **Pizarra Profunda** (`-c-1`): tooltips, popup del calendario, barras de días futuros, pista vacía de la barra segmentada, fondo de la variación a la baja.
- **Ciruela Humo** (`-c-2`): relleno de selección (chip seleccionado, día de hoy en el calendario, barra de selección Modena) y estado "confirmar" de acciones destructivas; barras de días sin gasto.
- **Malva Ceniza** (`-c-3`): barras normales de la gráfica, texto tenue del año en el título, placeholder del monto, días de otro mes.
- **Riel Noche** (`-c-riel`): fondo del riel, más oscuro que la raíz para retroceder.
- **Panel Elevado** (`-c-panel`) y **Superficie Tabla** (`-c-superficie`): superficies un paso por encima de la raíz.
- **Texto Terciario** (`-c-texto-3`): entre `-c-3` y `-c-4`, para texto terciario legible (al menos 4.5:1 sobre la raíz): ejes, detalles, cabeceras de tabla, atajos, nav inactivo.
- **Línea** (`-c-linea`) y **Velo hover** (`-c-velo-hover`): lavanda hueso al 7 % y 4.5 %; divisores, bordes de 1 px y hover de filas. Los velos de interacción usan la misma base `rgba(226,221,231,α)` con α de 0.045 a 0.13.

### Named Rules
**Regla de la familia cerrada.** Ningún color fuera de la escalera gris violácea `#17151C → #E2DDE7` y sus mezclas. No hay rojo, verde, ámbar ni azul de sistema; tampoco para errores (el texto de error usa Lavanda Hueso en Medium).

**Regla de la única luz.** Lavanda Hueso como relleno se reserva a la acción principal de la zona, al día seleccionado del calendario y al marcador de hoy en la gráfica. Si una pantalla nueva tiene dos rellenos claros compitiendo, uno sobra.

**Regla de forma antes que color.** Alza y baja se distinguen por triángulo (▲/▼ como SVG) y por luminosidad del fondo (velo claro al subir, `-c-1` al bajar), nunca por tono.

## Typography

**Display Font:** Figtree SemiBold (600)
**Body Font:** Figtree 400 y Medium 500
**Label/Mono Font:** IBM Plex Mono Medium 500 / SemiBold 600 (cifras)

**Character:** Figtree es geométrica, cálida y legible a tamaño chico; Plex Mono da a las cifras la lectura alineada de un estado de cuenta. Ambas son OFL, subconjunto latin (`ui/fuentes/`).

En JavaFX cada peso se registra como familia aparte y el peso se elige por nombre, nunca con `-fx-font-weight`: `"Figtree Light"` (= 400, nombre heredado), `"Figtree Light Medium"` (500), `"Figtree Light SemiBold"` (600), `"IBM Plex Mono Medium"`, `"IBM Plex Mono SemiBold"`. En React Native usar `Figtree` / `IBMPlexMono` con el peso real.

### Hierarchy
- **Cifra hero** (600, 64 px; decimales 600, 34 px en Lila Niebla): el total del mes, partido en entero y decimales.
- **Cifra campo** (600, 42 px): el monto que se está escribiendo en el panel de captura.
- **Título** (600, 34 px): mes + año (año en Malva Ceniza), nombre de vista o de categoría.
- **Título de panel** (600, 20 px) y **título de sección** (600, 17 px): "Nuevo gasto", "Por categoría", "Movimientos". Título de estado vacío 600, 16 px.
- **Body** (400 / 500, 14 px): texto base; nombres en filas y leyendas en Medium. Botón ancho 600, 15 px; botón normal 600, 14 px.
- **Label** (500, 12-13 px): etiquetas de campo, cabeceras de tabla, chips (13 px).
- **Detalle** (400, 12-13 px) y **micro** (400, 11 px; estado 10 px): conteos, porcentajes, ejes, atajos de teclado.
- **Cifras tabulares** (Plex Mono 500, 15 px en filas, 14 px en leyendas, 12 px en subtotales y tooltips; 600, 18 px en el total de "Hoy").

### Named Rules
**Regla de la cifra en mono.** Todo monto que se compara en columna va en IBM Plex Mono: JavaFX no tiene `tnum` ni `letter-spacing`, así que la alineación tabular se obtiene con la fuente. Las cifras hero (64 px, 42 px) van en Figtree porque se leen solas.

**Regla del peso por nombre.** Nunca pedir un peso con `-fx-font-weight`; elegir la familia registrada de ese peso.

## Layout

Ventana de tres zonas fijas, sin modales:
- **Riel** de 84 px a la izquierda (padding 22/10/20/10, ítems de 64 px, gap 10): marca, Resumen, Categorías y el estado "Local" abajo.
- **Centro** flexible con scroll: padding 40 arriba, 44 a los lados, 56 abajo; 40 px entre secciones. El hero pone la cifra a la izquierda y la gráfica de barras por día a la derecha con 56 px entre ambas.
- **Panel de captura** de 360 px a la derecha, siempre visible (padding 34/28/22/28, 20 px entre bloques): monto arriba, campos y chips de categoría, tarjeta "Hoy" y la acción principal abajo, con la línea de atajos al pie.
- **Categorías** usa topología lista + detalle (Steam): lista fija de 320 px (fondo `rgba(18,16,22,0.55)`) y detalle con 28 px entre bloques.

Ritmo: gaps de 2 (texto apilado), 4, 8 (grupos compactos), 10, 14 (encabezado a contenido de sección), 16, 20, 28, 40 (secciones) y 56 (hero). Filas de tabla con padding 10/14; separador de día 18/14/8/14. La gráfica por día mide 128 px de alto, una columna por día con ancho porcentual igual.

## Elevation & Depth

Híbrido: capas tonales por defecto, con una luz difusa ambiental y sombras solo en lo que flota o en el panel de captura. El riel es más oscuro que la raíz (retrocede); panel y tablas un paso más claros (avanzan); campos de entrada vuelven a `-c-0` para leerse hundidos.

En JavaFX la **primera** capa de `-fx-background-color` se pinta **abajo** (al revés que CSS web): el sólido va primero y la luz o el relleno encima. Los bordes finos se hacen con dos capas (`-c-linea` + superficie con insets de 1 px).

### Shadow Vocabulary
- **Luz del hero** (`radial-gradient(center 24% 12%, radius 40%, rgba(226,221,231,0.13) 0%, rgba(226,221,231,0.04) 50%, transparent 100%)` sobre `-c-0`): resplandor difuso detrás del título y la cifra, como en la imagen de la paleta del usuario. Uno por ventana.
- **Panel de captura** (`dropshadow(gaussian, rgba(0,0,0,0.38), 26, 0, -8, 0)`): sombra suave hacia la izquierda que separa el panel fijo del centro.
- **Flotantes** (tooltip `dropshadow(gaussian, rgba(0,0,0,0.45), 14, 0, 0, 4)`; popup del calendario `dropshadow(gaussian, rgba(0,0,0,0.55), 26, 0, 0, 10)`): solo para lo que se superpone.

### Named Rules
**Regla de plano por defecto.** Tarjetas, tablas, chips y botones no llevan sombra; se separan por tono y por la línea de 1 px. La sombra es para el panel fijo y lo que flota.

## Shapes

Curvas amables y consistentes por tamaño: píldora completa (999) para todo lo accionable de una línea (botones, chips, botones de ícono circulares, variación); 18 px para contenedores grandes (tabla, caja de monto), 16 px para el popup, 14 px para tarjetas chicas y nav, 12 px para campos, filas y elementos de lista, 9 px para días del calendario, 8 px para tooltip y botón de texto. Los datos usan esquinas mínimas: barras de día 3/3/1/1, tramos de barra segmentada 4 px (pista 6 px, alto 12 px), muestras de categoría 10 px con radio 3, puntos de 8 px redondos. Bordes internos con radio exterior − 1 para que el anillo de capa siga la curva (18/17, 14/13, 12/11).

## Components

### Buttons
Sobrios y táctiles: una sola píldora clara, todo lo demás translúcido.
- **Shape:** píldora completa (999 px).
- **Primary:** Lavanda Hueso con texto Carbón Violeta, Figtree 600; ancho completo en el panel (padding 14/20, 15 px). Forma de ícono: círculo de 38 px con el ícono en `-c-0` (crear categoría).
- **Hover / Focus:** hover aclara a `#f1edf4`, pressed oscurece a `#cdc6d4`. Foco solo con teclado: borde de 2 px Lila Niebla con `border-insets: -3` (anillo exterior que no cambia el tamaño). Sin cursor de mano (convención de escritorio).
- **Icono:** círculo de 38 px (30 px en la variante chica, ícono de 11 px en Lila Niebla) con velo al 7 %, 13 % en hover. **Píldora de texto** ("Hoy"): mismo velo, 38 px de alto, 13 px.
- **Texto:** transparente, Lila Niebla, 12 px, radio 8; hover con velo al 7 % y texto Lavanda Hueso ("Renombrar", "Eliminar").
- **Destructivo:** transparente en Lila Niebla; el primer clic lo pasa a estado confirmar (relleno Ciruela Humo, texto Lavanda Hueso) y solo el segundo ejecuta. Nunca rojo.

### Chips
- **Style:** píldora con velo al 6 %, texto Lila Niebla Medium 13 px, padding 7/13/7/11, punto de color de categoría de 8 px a 8 px del texto.
- **State:** hover velo 11 % y texto claro; seleccionado relleno Ciruela Humo con anillo interior de 1 px Lila Niebla (capas `-c-4, -c-2`, insets 0, 1). Foco con anillo exterior igual que los botones.

### Cards / Containers
- **Corner Style:** 18 px (tabla, caja de monto), 14 px (tarjeta "Hoy").
- **Background:** Superficie Tabla para la tabla de movimientos; `-c-0` para tarjetas dentro del panel.
- **Shadow Strategy:** plano (ver Elevation & Depth).
- **Border:** 1 px de `-c-linea` como capa de fondo inferior con insets 1.
- **Internal Padding:** tabla 6/8/8/8 con filas 10/14; tarjeta "Hoy" 14/18; caja de monto 16/20/12/20.

### Inputs / Fields
- **Style:** fondo `-c-0`, anillo de 1 px `-c-linea`, radio 12, padding 12/14, placeholder Lila Niebla, selección de texto `rgba(170,162,179,0.4)`. El monto es un campo sin fondo de 42 px dentro de la caja de monto. El DatePicker comparte el mismo tratamiento.
- **Focus:** el anillo pasa a Lila Niebla de 1.5 px cambiando las capas de fondo (`-c-4, -c-0` con insets 0, 1.5 y radios 12, 10.5): el foco no altera el tamaño ni empuja el layout.
- **Error:** texto Lavanda Hueso Medium 13 px bajo el campo; sin color de alarma.

### Navigation
- **Riel vertical:** ítems de 64 px con ícono SVG de 19 px sobre etiqueta Medium 11 px, radio 14. Inactivo en Texto Terciario, hover en Lila Niebla, seleccionado con velo al 8 % y ícono+texto en Lavanda Hueso. Foco por teclado con borde Lila Niebla. Atajo global Ctrl+N vuelve al panel de captura.

### Gráfica por día (signature)
Barras de una columna por día, 128 px de alto: días con gasto en Malva Ceniza, el pico en Lila Niebla, hoy en Lavanda Hueso, días sin gasto como pista de 3 px en Ciruela Humo y días futuros en Pizarra Profunda. Línea de promedio punteada (segmentos 4/4) en Lila Niebla al 55 %. Leyenda con muestra cuadrada del pico y trazo punteado del promedio. Tooltip en Plex Mono.

### Barra segmentada por categoría (signature)
Barra de 12 px partida en tramos del color de cada categoría con 3 px entre tramos y radio 4; debajo, leyenda en columnas con muestra, nombre Medium, conteo, monto en mono y porcentaje.

### Tabla de movimientos (signature)
Contenedor de 18 px con cabeceras Medium 12 px en Texto Terciario, separadores de día (estilo listas agrupadas de Apple) con subtotal en mono a la derecha, filas con línea superior de 1 px, hover en velo 4.5 % y fila seleccionada (en edición) en velo 10 %.

## Do's and Don'ts

### Do:
- **Do** derivar cualquier color nuevo mezclando la escalera `#17151C → #E2DDE7`; para velos usar `rgba(226,221,231,α)` con α entre 0.045 y 0.13.
- **Do** dejar un solo relleno Lavanda Hueso por zona: la acción principal.
- **Do** poner toda cifra comparable en IBM Plex Mono y el peso por nombre de familia.
- **Do** dibujar foco y bordes como capas de fondo o con `border-insets` negativos para que el tamaño no cambie.
- **Do** pedir confirmación en dos clics para borrar, cambiando el botón a Ciruela Humo.
- **Do** dibujar íconos como trazos SVG en `-fx-shape` (enum `Iconos`, retícula 24) con el color heredado del estado.
- **Do** recordar que en JavaFX la primera capa de fondo queda abajo: sólido primero, luz encima.

### Don't:
- **Don't** usar rojo, verde u otro color semántico para alza, baja, error o borrar.
- **Don't** abrir modales para registrar o editar: todo pasa en el panel de captura fijo.
- **Don't** usar `:first-child`, `letter-spacing` ni `font-feature-settings`: JavaFX CSS no los soporta.
- **Don't** cambiar el cursor a mano en botones; es app de escritorio.
- **Don't** usar `#E2DDE7` como color de categoría: es el del texto y de la acción principal.
- **Don't** poner sombra en tarjetas, chips o filas; la sombra es del panel fijo y de lo que flota.
- **Don't** componer la vista como un dashboard de tarjetas iguales.
