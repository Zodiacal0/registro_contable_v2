package com.registrogastos.ui;

import com.registrogastos.dominio.Categoria;
import com.registrogastos.dominio.Gasto;
import com.registrogastos.servicio.ResumenMes;
import com.registrogastos.servicio.TotalCategoria;
import javafx.animation.Animation;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Categorías con la topología de la biblioteca de Steam: lista a la izquierda, detalle de
 * la elegida a la derecha (sus números y sus gastos del mes). Renombrar y eliminar viven
 * en el encabezado del detalle, no repetidos en cada elemento.
 */
final class VistaCategorias extends StackPane implements Revelable {

    private final ModeloUi modelo;
    private final Esqueleto esqueleto = Esqueleto.categorias();
    private final VBox columnaLista;
    private Animation revelando;
    /** Id de la categoría abierta en el detalle; null = la primera de la lista. */
    private final ObjectProperty<String> elegidaId = new SimpleObjectProperty<>();
    private final VBox lista = new VBox(2);
    private final VBox detalle = new VBox(28);
    private final Label subtitulo = Nodos.etiqueta("", "subtitulo");
    private final Label error = Nodos.etiqueta("", "error");

    VistaCategorias(ModeloUi modelo) {
        this.modelo = modelo;

        var nueva = new TextField();
        nueva.setPromptText("Nueva categoría");
        nueva.getStyleClass().add("campo");
        nueva.setOnAction(e -> crear(nueva));
        HBox.setHgrow(nueva, Priority.ALWAYS);
        var agregar = Iconos.MAS.boton("Agregar categoría", () -> crear(nueva));
        agregar.getStyleClass().add("boton-icono-primario");
        var alta = new HBox(8, nueva, agregar);
        alta.setAlignment(Pos.CENTER_LEFT);
        error.managedProperty().bind(error.textProperty().isNotEmpty());

        var desplazable = new ScrollPane(lista);
        desplazable.setFitToWidth(true);
        desplazable.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        desplazable.getStyleClass().add("vista");
        DesplazamientoSuave.aplicar(desplazable);
        VBox.setVgrow(desplazable, Priority.ALWAYS);

        columnaLista = new VBox(18,
                new VBox(4, Nodos.etiqueta("Categorías", "titulo"), subtitulo),
                new VBox(8, alta, error),
                desplazable);
        columnaLista.getStyleClass().add("biblioteca-lista");

        detalle.getStyleClass().add("contenido");
        var columnaDetalle = new ScrollPane(detalle);
        columnaDetalle.setFitToWidth(true);
        columnaDetalle.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        columnaDetalle.getStyleClass().add("vista");
        DesplazamientoSuave.aplicar(columnaDetalle);
        HBox.setHgrow(columnaDetalle, Priority.ALWAYS);

        // Capas: las dos columnas y, encima, el esqueleto de carga
        var columnas = new HBox(columnaLista, columnaDetalle);
        getChildren().addAll(columnas, esqueleto);
        setAlignment(Pos.TOP_LEFT);
        getStyleClass().add("biblioteca");

        modelo.resumenProperty().addListener((obs, anterior, resumen) -> pintar());
        elegidaId.addListener((obs, anterior, id) -> pintar());
        pintar();
    }

    /** Al entrar a la vista: esqueleto y luego la lista y cada bloque del detalle en cascada. */
    @Override
    public void revelar() {
        if (revelando != null) {
            revelando.stop();
        }
        var secciones = new ArrayList<Node>();
        secciones.add(columnaLista);
        secciones.addAll(detalle.getChildren());
        revelando = Movimiento.revelar(esqueleto, secciones);
    }

    private void pintar() {
        ResumenMes r = modelo.resumen();
        subtitulo.setText(r.categorias().size() + " categorías  ·  números de " + Formato.nombreMes(r.mes()));
        Map<String, TotalCategoria> totales = r.porCategoria().stream()
                .filter(t -> !t.sinCategoria())
                .collect(Collectors.toMap(t -> t.categoria().id(), Function.identity()));

        Optional<Categoria> elegida = r.categorias().stream()
                .filter(c -> c.id().equals(elegidaId.get()))
                .findFirst()
                .or(() -> r.categorias().stream().findFirst());

        lista.getChildren().clear();
        r.categorias().forEach(c -> lista.getChildren().add(
                itemLista(c, totales.get(c.id()), elegida.filter(c::equals).isPresent())));

        detalle.getChildren().setAll(elegida
                .map(c -> pintarDetalle(c, Optional.ofNullable(totales.get(c.id())), r))
                .orElseGet(() -> List.of(Nodos.etiqueta("Crea tu primera categoría con el campo de la izquierda.",
                        "vacio-texto"))));
    }

    private Node itemLista(Categoria categoria, TotalCategoria total, boolean elegida) {
        var textos = new VBox(2,
                Nodos.etiqueta(categoria.nombre(), "leyenda-nombre"),
                Nodos.etiqueta(total == null ? "Sin gastos" : cantidad(total.movimientos()), "leyenda-detalle"));
        var cifras = new VBox(2,
                Nodos.etiqueta(total == null ? "—" : Formato.monto(total.total()), "leyenda-monto"),
                Nodos.etiqueta(total == null ? "0%" : Formato.porcentaje(total.proporcion()), "leyenda-detalle"));
        cifras.setAlignment(Pos.TOP_RIGHT);

        var item = new HBox(12, muestra(categoria.color(), "muestra"), textos, Nodos.espaciador(), cifras);
        item.setAlignment(Pos.TOP_LEFT);
        item.getStyleClass().add("lista-item");
        if (elegida) {
            item.getStyleClass().add("lista-item-elegido");
        }
        item.setOnMouseClicked(e -> elegidaId.set(categoria.id()));
        return item;
    }

    private List<Node> pintarDetalle(Categoria categoria, Optional<TotalCategoria> total, ResumenMes r) {
        var nombre = Nodos.etiqueta(categoria.nombre(), "titulo");
        var resumen = Nodos.etiqueta(total
                .map(t -> cantidad(t.movimientos()) + "  ·  " + Formato.monto(t.total()) + "  ·  "
                        + Formato.porcentaje(t.proporcion()) + " de " + Formato.nombreMes(r.mes()))
                .orElse("Sin gastos en " + Formato.nombreMes(r.mes())), "subtitulo");
        resumen.setWrapText(true);
        var textos = new VBox(4, nombre, resumen);

        var renombrar = Nodos.boton("Renombrar", () -> editarNombre(textos, nombre, categoria), "boton-texto");
        var eliminar = new Button("Eliminar");
        eliminar.getStyleClass().addAll("boton", "boton-texto");
        eliminar.setOnAction(e -> eliminar(eliminar, categoria));
        // Acciones bajo el resumen: en una sola fila no caben sin recortar el texto
        var acciones = new HBox(4, renombrar, eliminar);
        acciones.setTranslateX(-8); // alinea el texto del botón (no su fondo) con el título
        textos.getChildren().add(acciones);
        HBox.setHgrow(textos, Priority.ALWAYS);

        var encabezado = new HBox(textos);
        encabezado.getStyleClass().add("encabezado");

        List<Gasto> gastos = r.gastos().stream()
                .filter(g -> Objects.equals(g.categoriaId(), categoria.id()))
                .toList();

        // Su lugar en el mes: la barra segmentada con esta categoría encendida y el resto apagado
        var parte = new HBox(3);
        parte.getStyleClass().add("barra-segmentada");
        for (TotalCategoria t : r.porCategoria()) {
            var tramo = new Region();
            tramo.getStyleClass().add("tramo");
            boolean esta = !t.sinCategoria() && t.categoria().id().equals(categoria.id());
            tramo.setStyle("-fx-background-color: " + (esta ? categoria.color() : "-c-1") + ";");
            tramo.setMinWidth(4);
            tramo.prefWidthProperty().bind(parte.widthProperty().multiply(t.proporcion()));
            parte.getChildren().add(tramo);
        }

        // Sus días: el mismo componente del resumen, alimentado solo con sus gastos
        var resumenCategoria = new ResumenMes(r.mes(), gastos, r.categorias(),
                total.map(TotalCategoria::total).orElse(BigDecimal.ZERO), BigDecimal.ZERO,
                r.diasConsiderados(), List.of());
        var dias = new VBox(14,
                new GraficaDias(r.mes(), resumenCategoria.porDia(), resumenCategoria.promedioDiario(), modelo.hoy(),
                        false, null),
                GraficaDias.leyenda(r.mes(), resumenCategoria.porDia(), resumenCategoria.promedioDiario(), modelo.hoy()));
        var seccionDias = new VBox(16,
                Nodos.etiqueta("Por día", "seccion-titulo"),
                r.porCategoria().isEmpty() ? new Region() : parte,
                dias);
        var tabla = new VBox();
        tabla.getStyleClass().add("tabla");
        if (gastos.isEmpty()) {
            var vacio = new VBox(Nodos.etiqueta("Sin gastos de " + categoria.nombre() + " en "
                    + Formato.nombreMes(r.mes()) + ".", "vacio-texto"));
            vacio.getStyleClass().add("tabla-vacia");
            tabla.getChildren().add(vacio);
        } else {
            gastos.forEach(g -> tabla.getChildren().add(filaGasto(g)));
        }
        var seccion = new VBox(14,
                Nodos.etiqueta("Gastos de " + Formato.nombreMes(r.mes()), "seccion-titulo"), tabla);
        return List.of(encabezado, seccionDias, seccion);
    }

    private Node filaGasto(Gasto gasto) {
        var fecha = Nodos.etiqueta(Formato.fechaCorta(gasto.fecha(), modelo.hoy()), "celda");
        fecha.setMinWidth(90);
        var fila = new HBox(fecha, Nodos.etiqueta(gasto.descripcion(), "celda-titulo"), Nodos.espaciador(),
                Nodos.etiqueta(Formato.monto(gasto.monto()), "celda-monto"));
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().add("tabla-fila");
        fila.setOnMouseClicked(e -> modelo.seleccionadoProperty().set(gasto)); // se edita en el panel
        return fila;
    }

    private static Region muestra(String color, String clase) {
        var muestra = new Region();
        muestra.getStyleClass().add(clase);
        muestra.setStyle("-fx-background-color: " + color + ";");
        return muestra;
    }

    private static String cantidad(int movimientos) {
        return movimientos == 1 ? "1 gasto" : movimientos + " gastos";
    }

    /** Cambia el título por un campo: Enter guarda, Esc cancela. */
    private void editarNombre(VBox contenedor, Label nombre, Categoria categoria) {
        var campo = new TextField(categoria.nombre());
        campo.getStyleClass().add("campo");
        contenedor.getChildren().set(0, campo);
        campo.requestFocus();
        campo.selectAll();
        campo.setOnAction(e -> ejecutar(() -> modelo.renombrarCategoria(categoria.id(), campo.getText())));
        campo.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                contenedor.getChildren().set(0, nombre);
                e.consume();
            }
        });
    }

    private void eliminar(Button boton, Categoria categoria) {
        if (!boton.getStyleClass().contains("boton-texto-confirmar")) {
            boton.setText("Confirmar eliminación");
            boton.getStyleClass().add("boton-texto-confirmar");
            return;
        }
        ejecutar(() -> {
            modelo.eliminarCategoria(categoria.id());
            elegidaId.set(null);
        });
    }

    private void crear(TextField campo) {
        ejecutar(() -> {
            modelo.crearCategoria(campo.getText());
            campo.clear();
        });
    }

    private void ejecutar(Runnable accion) {
        try {
            accion.run();
            error.setText("");
        } catch (IllegalArgumentException e) {
            error.setText(e.getMessage());
        }
    }
}
