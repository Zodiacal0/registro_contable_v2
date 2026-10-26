package com.registrogastos.ui;

import com.registrogastos.dominio.Categoria;
import com.registrogastos.dominio.Gasto;
import com.registrogastos.servicio.ProximoCobro;
import com.registrogastos.servicio.ResumenMes;
import com.registrogastos.servicio.TotalCategoria;
import javafx.animation.Animation;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Pantalla principal, de arriba abajo:
 * 1. Mes y navegación.
 * 2. Hero sin caja: saldo partido que "cuenta" hasta su valor + barras por día interactivas.
 * 3. Por categoría: barra segmentada + leyenda (clic en una categoría = filtrar).
 * 4. Movimientos agrupados por día, con búsqueda y filtros (clic en un gasto = editar).
 *
 * Repintado en dos niveles: un cambio de datos repinta todo; un cambio de filtro o de
 * selección solo repinta la tabla. Así la búsqueda no pierde el foco al escribir y las
 * animaciones no se repiten por cada tecla.
 */
final class VistaResumen extends ScrollPane implements Revelable {

    private static final double COL_CATEGORIA = 190;
    private static final double COL_MONTO = 120;

    private final ModeloUi modelo;
    private final VBox contenido = new VBox();

    // Movimientos: se construye una vez y solo cambian sus partes internas
    private final VBox seccionMovimientos = new VBox(14);
    private final TextField busqueda = new TextField();
    private final Label contador = Nodos.etiqueta("", "seccion-detalle");
    private final HBox filtros = new HBox(8);
    private final VBox tabla = new VBox();

    // Estado de lo último pintado, para decidir qué animar
    private YearMonth mesPintado;
    private BigDecimal totalMostrado = BigDecimal.ZERO;
    private GraficaDias grafica;
    private final Map<String, Node> itemsLeyenda = new HashMap<>();

    // Carga tipo template: el esqueleto va encima del contenido y se desvanece al revelarlo
    private final Esqueleto esqueleto = Esqueleto.resumen();
    private Animation revelando;

    VistaResumen(ModeloUi modelo) {
        this.modelo = modelo;
        contenido.getStyleClass().add("contenido");
        var capas = new StackPane(contenido, esqueleto);
        capas.setAlignment(Pos.TOP_CENTER);
        setContent(capas);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        getStyleClass().add("vista");
        DesplazamientoSuave.aplicar(this);
        construirMovimientos();

        modelo.resumenProperty().addListener((obs, anterior, resumen) -> pintar());
        modelo.seleccionadoProperty().addListener((obs, anterior, gasto) -> pintarTabla());
        modelo.busquedaProperty().addListener((obs, anterior, texto) -> pintarTabla());
        modelo.diaFiltroProperty().addListener((obs, anterior, dia) -> {
            grafica.marcarDia(dia);
            pintarTabla();
        });
        modelo.categoriaFiltroProperty().addListener((obs, anterior, id) -> {
            marcarLeyenda();
            pintarTabla();
        });
        pintar();
    }

    private void pintar() {
        ResumenMes r = modelo.resumen();
        boolean mesNuevo = !r.mes().equals(mesPintado);
        mesPintado = r.mes();
        var secciones = new ArrayList<>(List.of(encabezado(r), hero(r, mesNuevo), seccionCategorias(r)));
        if (!modelo.recurrencias().isEmpty()) {
            secciones.add(seccionProximos());
        }
        secciones.add(seccionMovimientos);
        contenido.getChildren().setAll(secciones);
        pintarTabla();
        if (mesNuevo) {
            revelar(); // al abrir la app o cambiar de mes; no con cada gasto nuevo
        }
    }

    @Override
    public void revelar() {
        if (revelando != null) {
            revelando.stop(); // una revelación nueva reemplaza a la que iba a medias
        }
        revelando = Movimiento.revelar(esqueleto, List.copyOf(contenido.getChildren()));
    }

    // ---- 1. Encabezado ----

    private Node encabezado(ResumenMes r) {
        var mes = Nodos.etiqueta(Formato.tituloMes(r.mes()), "titulo");
        var anio = Nodos.etiqueta(String.valueOf(r.mes().getYear()), "titulo", "titulo-tenue");
        var navegacion = new HBox(8);
        if (!modelo.enMesActual()) {
            // A la izquierda: las flechas no se mueven al aparecer, así un doble clic no cae en otro control
            navegacion.getChildren().add(Nodos.boton("Hoy", modelo::irAMesActual, "boton-pildora"));
        }
        navegacion.getChildren().addAll(
                Iconos.ANTERIOR.boton("Mes anterior", modelo::mesAnterior),
                Iconos.SIGUIENTE.boton("Mes siguiente", modelo::mesSiguiente));
        navegacion.setAlignment(Pos.CENTER);

        var fila = new HBox(10, mes, anio, Nodos.espaciador(), navegacion);
        fila.setAlignment(Pos.BASELINE_LEFT);
        fila.getStyleClass().add("encabezado");
        return fila;
    }

    // ---- 2. Hero ----

    private Node hero(ResumenMes r, boolean mesNuevo) {
        var entero = Nodos.etiqueta("", "saldo-entero");
        var decimales = Nodos.etiqueta("", "saldo-decimales");
        var saldo = new HBox(entero, decimales);
        saldo.setAlignment(Pos.BASELINE_LEFT);
        // El saldo cuenta desde lo que se mostraba antes (o desde cero al abrir la app)
        Movimiento.contar(entero, decimales, totalMostrado, r.total());
        totalMostrado = r.total();

        // El contexto va debajo del número, no como etiqueta encima: el título ya dice el mes
        var izquierda = new VBox(10, saldo, variacion(r), Nodos.etiqueta(contexto(r), "etiqueta"));
        izquierda.setMinWidth(Region.USE_PREF_SIZE);

        grafica = new GraficaDias(r.mes(), r.porDia(), r.promedioDiario(), modelo.hoy(), mesNuevo, modelo::alternarDia);
        grafica.marcarDia(modelo.diaFiltroProperty().get());

        // Lectura sobre la gráfica: el día bajo el mouse, el día filtrado o una pista de uso
        var lectura = new Label();
        lectura.getStyleClass().add("grafica-lectura");
        lectura.textProperty().bind(Bindings.createStringBinding(() -> {
            if (!grafica.lecturaProperty().get().isEmpty()) {
                return grafica.lecturaProperty().get();
            }
            LocalDate dia = modelo.diaFiltroProperty().get();
            return dia != null
                    ? "Mostrando " + Formato.dia(dia, modelo.hoy()).toLowerCase() + "  ·  clic de nuevo para ver todo"
                    : "Pasa el mouse por un día para verlo  ·  clic para filtrar";
        }, grafica.lecturaProperty(), modelo.diaFiltroProperty()));

        var derecha = new VBox(12, lectura, grafica,
                GraficaDias.leyenda(r.mes(), r.porDia(), r.promedioDiario(), modelo.hoy()));
        HBox.setHgrow(derecha, Priority.ALWAYS);

        var hero = new HBox(56, izquierda, derecha);
        hero.setAlignment(Pos.BOTTOM_LEFT);
        hero.getStyleClass().add("hero");
        return hero;
    }

    private static String contexto(ResumenMes r) {
        int n = r.gastos().size();
        if (n == 0) {
            return "Sin gastos registrados en " + Formato.nombreMes(r.mes());
        }
        return (n == 1 ? "1 gasto" : n + " gastos") + " en " + r.diasConsiderados()
                + (r.diasConsiderados() == 1 ? " día" : " días");
    }

    /** La dirección la da el triángulo; el color se mantiene dentro de la paleta. */
    private static Node variacion(ResumenMes r) {
        String mesAnterior = Formato.nombreMes(r.mes().minusMonths(1));
        var fila = new HBox(8);
        fila.setAlignment(Pos.CENTER_LEFT);
        if (r.gastos().isEmpty()) {
            return fila; // sin gastos no hay nada que comparar
        }
        r.variacion().ifPresentOrElse(v -> {
            boolean sube = v.signum() > 0;
            var chip = new Label(v.abs() + "%", (sube ? Iconos.SUBE : Iconos.BAJA).crear("icono-chico"));
            chip.getStyleClass().addAll("variacion", sube ? "variacion-sube" : "variacion-baja");
            fila.getChildren().addAll(chip, Nodos.etiqueta((sube ? "más que " : "menos que ") + mesAnterior
                    + "  ·  " + Formato.monto(r.totalMesAnterior()), "etiqueta"));
        }, () -> fila.getChildren().add(Nodos.etiqueta("Sin gastos en " + mesAnterior + " para comparar", "etiqueta")));
        return fila;
    }

    // ---- 3. Por categoría ----

    private Node seccionCategorias(ResumenMes r) {
        itemsLeyenda.clear();
        var encabezado = encabezadoSeccion(Nodos.etiqueta("Por categoría", "seccion-titulo"),
                Nodos.etiqueta(r.porCategoria().isEmpty() ? "" : "Clic en una para filtrar", "seccion-detalle"));
        if (r.porCategoria().isEmpty()) {
            return new VBox(14, encabezado, barraVacia(),
                    Nodos.etiqueta("El reparto por categoría aparece con el primer gasto del mes.", "seccion-detalle"));
        }

        // Barra segmentada: cada tramo crece en proporción a su total
        var barra = new HBox(3);
        barra.getStyleClass().add("barra-segmentada");
        for (TotalCategoria t : r.porCategoria()) {
            var tramo = new Region();
            tramo.getStyleClass().add("tramo");
            tramo.setStyle("-fx-background-color: " + Paleta.colorDe(t.categoria()) + ";");
            tramo.setMinWidth(4);
            tramo.prefWidthProperty().bind(barra.widthProperty().multiply(t.proporcion()));
            barra.getChildren().add(tramo);
        }

        var leyenda = new GridPane();
        leyenda.setHgap(16);
        leyenda.setVgap(4);
        for (int c = 0; c < 3; c++) {
            var columna = new ColumnConstraints();
            columna.setPercentWidth(100 / 3.0);
            leyenda.getColumnConstraints().add(columna);
        }
        for (int i = 0; i < r.porCategoria().size(); i++) {
            leyenda.add(itemLeyenda(r.porCategoria().get(i)), i % 3, i / 3);
        }
        marcarLeyenda();
        // Margen negativo: el texto de cada item (no su fondo de hover) queda alineado con la barra
        VBox.setMargin(leyenda, new Insets(0, -10, 0, -10));
        return new VBox(12, encabezado, barra, leyenda);
    }

    private static Node barraVacia() {
        var barra = new HBox();
        barra.getStyleClass().addAll("barra-segmentada", "barra-segmentada-vacia");
        return barra;
    }

    private Node itemLeyenda(TotalCategoria t) {
        String nombre = t.sinCategoria() ? "Sin categoría" : t.categoria().nombre();
        String clave = t.sinCategoria() ? ModeloUi.SIN_CATEGORIA : t.categoria().id();
        var textos = new VBox(2,
                Nodos.etiqueta(nombre, "leyenda-nombre"),
                Nodos.etiqueta(t.movimientos() + (t.movimientos() == 1 ? " gasto" : " gastos"), "leyenda-detalle"));
        var cifras = new VBox(2,
                Nodos.etiqueta(Formato.monto(t.total()), "leyenda-monto"),
                Nodos.etiqueta(Formato.porcentaje(t.proporcion()), "leyenda-detalle"));
        cifras.setAlignment(Pos.TOP_RIGHT);
        var fila = new HBox(10, muestra(t.categoria()), textos, Nodos.espaciador(), cifras);
        fila.setAlignment(Pos.TOP_LEFT);
        fila.getStyleClass().add("leyenda-item");
        fila.setOnMouseClicked(e -> modelo.alternarCategoria(clave));
        itemsLeyenda.put(clave, fila);
        return fila;
    }

    /** Con un filtro de categoría activo, la elegida se marca y las demás se apagan. */
    private void marcarLeyenda() {
        String elegida = modelo.categoriaFiltroProperty().get();
        itemsLeyenda.forEach((clave, item) -> {
            item.getStyleClass().removeAll("leyenda-item-elegida", "leyenda-item-apagada");
            if (elegida != null) {
                item.getStyleClass().add(clave.equals(elegida) ? "leyenda-item-elegida" : "leyenda-item-apagada");
            }
        });
    }

    private static Region muestra(Categoria categoria) {
        var muestra = new Region();
        muestra.getStyleClass().add("muestra");
        muestra.setStyle("-fx-background-color: " + Paleta.colorDe(categoria) + ";");
        return muestra;
    }

    // ---- Próximos cobros (solo si hay recurrentes) ----

    /** Los siguientes 4 cobros de los próximos 30 días, en tarjetas como los "programados" de Apple Wallet. */
    private Node seccionProximos() {
        var encabezado = encabezadoSeccion(Nodos.etiqueta("Próximos cobros", "seccion-titulo"),
                Nodos.etiqueta("≈ " + Formato.monto(modelo.costoMensualRecurrente()) + " al mes en recurrentes",
                        "seccion-detalle"));
        List<ProximoCobro> proximos = modelo.proximosCobros(30).stream().limit(4).toList();
        if (proximos.isEmpty()) {
            return new VBox(14, encabezado, Nodos.etiqueta("Sin cobros en los próximos 30 días.", "seccion-detalle"));
        }

        var tarjetas = new HBox(12);
        for (ProximoCobro p : proximos) {
            var categoria = modelo.resumen().categoria(p.recurrencia().categoriaId()).orElse(null);
            var cuando = new HBox(8, Nodos.punto(Paleta.colorDe(categoria)),
                    Nodos.etiqueta(Formato.cuando(p.fecha(), modelo.hoy()), "cobro-cuando"));
            cuando.setAlignment(Pos.CENTER_LEFT);
            var tarjeta = new VBox(8, cuando,
                    Nodos.etiqueta(p.recurrencia().descripcion(), "cobro-nombre"),
                    Nodos.etiqueta(Formato.monto(p.recurrencia().monto()), "cobro-monto"));
            tarjeta.getStyleClass().add("cobro-tarjeta");
            tarjeta.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(tarjeta, Priority.ALWAYS);
            tarjetas.getChildren().add(tarjeta);
        }
        return new VBox(14, encabezado, tarjetas);
    }

    // ---- 4. Movimientos ----

    private void construirMovimientos() {
        busqueda.setPromptText("Buscar gasto");
        busqueda.getStyleClass().addAll("campo", "campo-busqueda");
        busqueda.setPrefWidth(240);
        busqueda.textProperty().bindBidirectional(modelo.busquedaProperty());

        var titulo = new HBox(12, Nodos.etiqueta("Movimientos", "seccion-titulo"), contador);
        titulo.setAlignment(Pos.BASELINE_LEFT);
        var encabezado = new HBox(titulo, Nodos.espaciador(), busqueda);
        encabezado.setAlignment(Pos.CENTER_LEFT);

        filtros.setAlignment(Pos.CENTER_LEFT);
        filtros.managedProperty().bind(Bindings.isNotEmpty(filtros.getChildren()));
        filtros.visibleProperty().bind(filtros.managedProperty());
        tabla.getStyleClass().add("tabla");
        seccionMovimientos.getChildren().addAll(encabezado, filtros, tabla);
    }

    private void pintarTabla() {
        ResumenMes r = modelo.resumen();
        List<Gasto> gastos = modelo.movimientosFiltrados();
        String nuevo = modelo.tomarUltimoRegistrado();

        contador.setText(r.gastos().isEmpty() ? ""
                : modelo.hayFiltros() ? gastos.size() + " de " + r.gastos().size()
                : r.gastos().size() + " este mes");
        pintarFiltros(r);
        tabla.getChildren().clear();

        if (r.gastos().isEmpty()) {
            tabla.getChildren().add(vacio("Aún no hay movimientos en " + Formato.nombreMes(r.mes()),
                    "Registra el primero desde el panel de la derecha.", false));
            return;
        }
        if (gastos.isEmpty()) {
            tabla.getChildren().add(vacio("Ningún movimiento coincide",
                    "Prueba con otra búsqueda o quita los filtros.", true));
            return;
        }

        var cabecera = new HBox(
                columna(Nodos.etiqueta("Gasto", "tabla-cabecera"), -1, Pos.CENTER_LEFT),
                columna(Nodos.etiqueta("Categoría", "tabla-cabecera"), COL_CATEGORIA, Pos.CENTER_LEFT),
                columna(Nodos.etiqueta("Monto", "tabla-cabecera"), COL_MONTO, Pos.CENTER_RIGHT));
        cabecera.getStyleClass().add("tabla-fila-cabecera");
        tabla.getChildren().add(cabecera);

        // LinkedHashMap conserva el orden (del más reciente al más antiguo) que trae el resumen
        Map<LocalDate, List<Gasto>> porDia = gastos.stream()
                .collect(Collectors.groupingBy(Gasto::fecha, LinkedHashMap::new, Collectors.toList()));
        porDia.forEach((fecha, delDia) -> {
            tabla.getChildren().add(cabeceraDia(fecha, delDia));
            delDia.forEach(g -> tabla.getChildren().add(fila(g, r, g.id().equals(nuevo))));
        });
    }

    /** Píldoras de los filtros activos; cada una se quita con su ✕. */
    private void pintarFiltros(ResumenMes r) {
        filtros.getChildren().clear();
        LocalDate dia = modelo.diaFiltroProperty().get();
        if (dia != null) {
            filtros.getChildren().add(pildoraFiltro(Formato.dia(dia, modelo.hoy()),
                    () -> modelo.diaFiltroProperty().set(null)));
        }
        String categoria = modelo.categoriaFiltroProperty().get();
        if (categoria != null) {
            String nombre = categoria.equals(ModeloUi.SIN_CATEGORIA) ? "Sin categoría"
                    : r.categoria(categoria).map(Categoria::nombre).orElse("Categoría");
            filtros.getChildren().add(pildoraFiltro(nombre, () -> modelo.categoriaFiltroProperty().set(null)));
        }
        if (filtros.getChildren().size() > 1) {
            filtros.getChildren().add(Nodos.boton("Quitar filtros", modelo::quitarFiltros, "boton-texto"));
        }
    }

    private static Button pildoraFiltro(String texto, Runnable quitar) {
        var pildora = new Button(texto, Iconos.CERRAR.crear("icono-chico"));
        pildora.setContentDisplay(ContentDisplay.RIGHT);
        pildora.getStyleClass().addAll("boton", "filtro-pildora");
        pildora.setAccessibleText("Quitar filtro " + texto);
        pildora.setOnAction(e -> quitar.run());
        return pildora;
    }

    private Node vacio(String titulo, String texto, boolean conAccion) {
        var vacio = new VBox(6, Nodos.etiqueta(titulo, "vacio-titulo"), Nodos.etiqueta(texto, "vacio-texto"));
        if (conAccion) {
            var quitar = Nodos.boton("Quitar filtros", modelo::quitarFiltros, "boton-pildora");
            VBox.setMargin(quitar, new Insets(8, 0, 0, 0));
            vacio.getChildren().add(quitar);
        }
        vacio.getStyleClass().add("tabla-vacia");
        return vacio;
    }

    /** Separador de día al estilo de las listas agrupadas de Apple, con el subtotal del día. */
    private Node cabeceraDia(LocalDate fecha, List<Gasto> gastos) {
        BigDecimal subtotal = gastos.stream().map(Gasto::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        var fila = new HBox(
                Nodos.etiqueta(Formato.dia(fecha, modelo.hoy()), "tabla-dia"),
                Nodos.espaciador(),
                Nodos.etiqueta(Formato.monto(subtotal), "tabla-dia-total"));
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().add("tabla-fila-dia");
        return fila;
    }

    private Node fila(Gasto gasto, ResumenMes r, boolean recienRegistrado) {
        Categoria categoria = r.categoria(gasto.categoriaId()).orElse(null);

        var categoriaCol = new HBox(8, Nodos.punto(Paleta.colorDe(categoria)),
                Nodos.etiqueta(categoria == null ? "Sin categoría" : categoria.nombre(), "celda"));
        categoriaCol.setAlignment(Pos.CENTER_LEFT);

        // Los cobros de una recurrencia llevan el ícono ↻ junto al nombre
        var descripcion = new HBox(8, Nodos.etiqueta(gasto.descripcion(), "celda-titulo"));
        descripcion.setAlignment(Pos.CENTER_LEFT);
        if (gasto.esRecurrente()) {
            descripcion.getChildren().add(Iconos.REPETIR.crear("icono-chico", "icono-recurrente"));
        }
        if (gasto.esAbono()) {
            descripcion.getChildren().add(Iconos.DEUDA.crear("icono-chico", "icono-recurrente"));
        }

        var fila = new HBox(
                columna(descripcion, -1, Pos.CENTER_LEFT),
                columna(categoriaCol, COL_CATEGORIA, Pos.CENTER_LEFT),
                columna(Nodos.etiqueta(Formato.monto(gasto.monto()), "celda-monto"), COL_MONTO, Pos.CENTER_RIGHT));
        fila.getStyleClass().add("tabla-fila");
        Gasto seleccionado = modelo.seleccionadoProperty().get();
        if (seleccionado != null && Objects.equals(seleccionado.id(), gasto.id())) {
            fila.getStyleClass().add("tabla-fila-seleccionada");
        }
        fila.setOnMouseClicked(e -> modelo.seleccionadoProperty().set(gasto));
        if (!recienRegistrado) {
            return fila;
        }

        // Gasto recién guardado: un destello detrás de la fila que se apaga solo
        var destello = new Region();
        destello.getStyleClass().add("fila-destello");
        destello.setMouseTransparent(true);
        Movimiento.resaltar(destello);
        return new StackPane(destello, fila);
    }

    /** Celda de ancho fijo; con ancho -1 crece para ocupar el sobrante (columna principal). */
    private static Node columna(Node contenido, double ancho, Pos alineacion) {
        var celda = new HBox(contenido);
        celda.setAlignment(alineacion);
        if (ancho < 0) {
            HBox.setHgrow(celda, Priority.ALWAYS);
            celda.setMinWidth(0);
            celda.setMaxWidth(Double.MAX_VALUE);
        } else {
            celda.setMinWidth(ancho);
            celda.setPrefWidth(ancho);
            celda.setMaxWidth(ancho);
        }
        return celda;
    }

    private static Node encabezadoSeccion(Node titulo, Node detalle) {
        var fila = new HBox(titulo, Nodos.espaciador(), detalle);
        fila.setAlignment(Pos.BASELINE_LEFT);
        return fila;
    }
}
