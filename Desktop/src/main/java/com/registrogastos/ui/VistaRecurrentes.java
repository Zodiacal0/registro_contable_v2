package com.registrogastos.ui;

import com.registrogastos.dominio.Categoria;
import com.registrogastos.dominio.Gasto;
import com.registrogastos.dominio.Recurrencia;
import javafx.animation.Animation;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Gastos recurrentes con la misma topología que Categorías (biblioteca de Steam): lista a
 * la izquierda con el costo mensual total, detalle a la derecha para editar, pausar y ver
 * los cobros que ya se registraron. Se crean desde el panel con "Se repite".
 */
final class VistaRecurrentes extends StackPane implements Revelable {

    private static final BigDecimal MESES_POR_ANIO = BigDecimal.valueOf(12);

    private final ModeloUi modelo;
    private final ObjectProperty<String> elegidaId = new SimpleObjectProperty<>();
    private final VBox lista = new VBox(2);
    private final VBox detalle = new VBox(32);
    private final Label costo = Nodos.etiqueta("", "subtitulo");
    private final VBox columnaLista;
    private Animation revelando;

    VistaRecurrentes(ModeloUi modelo) {
        this.modelo = modelo;

        var desplazable = new ScrollPane(lista);
        desplazable.setFitToWidth(true);
        desplazable.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        desplazable.getStyleClass().add("vista");
        DesplazamientoSuave.aplicar(desplazable);
        VBox.setVgrow(desplazable, Priority.ALWAYS);

        columnaLista = new VBox(18, new VBox(4, Nodos.etiqueta("Recurrentes", "titulo"), costo), desplazable);
        columnaLista.getStyleClass().add("biblioteca-lista");

        detalle.getStyleClass().add("contenido");
        var columnaDetalle = new ScrollPane(detalle);
        columnaDetalle.setFitToWidth(true);
        columnaDetalle.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        columnaDetalle.getStyleClass().add("vista");
        DesplazamientoSuave.aplicar(columnaDetalle);
        HBox.setHgrow(columnaDetalle, Priority.ALWAYS);

        getChildren().add(new HBox(columnaLista, columnaDetalle));
        setAlignment(Pos.TOP_LEFT);

        modelo.resumenProperty().addListener((obs, anterior, resumen) -> pintar());
        elegidaId.addListener((obs, anterior, id) -> pintar());
        pintar();
    }

    @Override
    public void revelar() {
        if (revelando != null) {
            revelando.stop();
        }
        var secciones = new ArrayList<Node>();
        secciones.add(columnaLista);
        secciones.addAll(detalle.getChildren());
        revelando = Movimiento.revelar(secciones);
    }

    private void pintar() {
        List<Recurrencia> recurrencias = modelo.recurrencias();
        BigDecimal mensual = modelo.costoMensualRecurrente();
        costo.setText(recurrencias.isEmpty() ? "Suscripciones, servicios y membresías"
                : "≈ " + Formato.monto(mensual) + " al mes  ·  ≈ " + Formato.monto(mensual.multiply(MESES_POR_ANIO)) + " al año");

        Optional<Recurrencia> elegida = recurrencias.stream()
                .filter(r -> r.id().equals(elegidaId.get()))
                .findFirst()
                .or(() -> recurrencias.stream().findFirst());

        lista.getChildren().clear();
        recurrencias.forEach(r -> lista.getChildren().add(itemLista(r, elegida.filter(r::equals).isPresent())));
        if (recurrencias.isEmpty()) {
            lista.getChildren().add(Nodos.etiqueta("Aún no tienes gastos recurrentes.", "vacio-texto"));
        }
        detalle.getChildren().setAll(elegida.map(this::pintarDetalle).orElseGet(this::vacio));
    }

    private Node itemLista(Recurrencia r, boolean elegida) {
        String estado = r.activa()
                ? Formato.frecuencia(r) + "  ·  " + Formato.fechaCorta(r.proximoCobro(modelo.hoy()), modelo.hoy())
                : "En pausa";
        var textos = new VBox(2, Nodos.etiqueta(r.descripcion(), "leyenda-nombre"), Nodos.etiqueta(estado, "leyenda-detalle"));
        textos.setMinWidth(0); // si falta espacio se recorta el texto...
        var monto = Nodos.etiqueta(modelo.monto(r), "leyenda-monto");
        monto.setMinWidth(Region.USE_PREF_SIZE); // ...nunca el monto

        var item = new HBox(12, muestra(r), textos, Nodos.espaciador(), monto);
        item.setAlignment(Pos.TOP_LEFT);
        item.getStyleClass().add("lista-item");
        if (elegida) {
            item.getStyleClass().add("lista-item-elegido");
        }
        if (!r.activa()) {
            item.getStyleClass().add("lista-item-pausado");
        }
        item.setOnMouseClicked(e -> elegidaId.set(r.id()));
        return item;
    }

    private Node muestra(Recurrencia r) {
        Categoria categoria = modelo.resumen().categoria(r.categoriaId()).orElse(null);
        var punto = new Region();
        punto.getStyleClass().add("muestra");
        punto.setStyle("-fx-background-color: " + Paleta.colorDe(categoria) + ";");
        return punto;
    }

    // ---- Detalle ----

    private List<Node> pintarDetalle(Recurrencia r) {
        List<Gasto> cobros = modelo.cobrosDe(r.id());
        return List.of(encabezado(r), formulario(r), historial(r, cobros));
    }

    private Node encabezado(Recurrencia r) {
        String resumen = modelo.monto(r) + "  ·  " + Formato.frecuencia(r) + "  ·  "
                + (r.activa()
                        ? "próximo cobro " + Formato.dia(r.proximoCobro(modelo.hoy()), modelo.hoy()).toLowerCase()
                        : "en pausa");
        var subtitulo = Nodos.etiqueta(resumen, "subtitulo");
        subtitulo.setWrapText(true);

        var pausar = Nodos.boton(r.activa() ? "Pausar" : "Reanudar", () -> {
            if (r.activa()) {
                modelo.pausarRecurrencia(r.id());
            } else {
                modelo.reanudarRecurrencia(r.id());
            }
        }, "boton-texto");
        var eliminar = new Button("Eliminar");
        eliminar.getStyleClass().addAll("boton", "boton-texto");
        eliminar.setOnAction(e -> {
            if (!eliminar.getStyleClass().contains("boton-texto-confirmar")) {
                eliminar.setText("Confirmar: se conservan los cobros ya registrados");
                eliminar.getStyleClass().add("boton-texto-confirmar");
                return;
            }
            modelo.eliminarRecurrencia(r.id());
            elegidaId.set(null);
        });
        var acciones = new HBox(4, pausar, eliminar);
        acciones.setTranslateX(-8); // alinea el texto del botón (no su fondo) con el título

        var encabezado = new VBox(4, Nodos.etiqueta(r.descripcion(), "titulo"), subtitulo, acciones);
        encabezado.getStyleClass().add("encabezado");
        return encabezado;
    }

    /** Editar afecta a los cobros futuros; los ya registrados no cambian. */
    private Node formulario(Recurrencia r) {
        var descripcion = new TextField(r.descripcion());
        descripcion.getStyleClass().add("campo");
        var monto = new TextField(r.monto().toPlainString());
        monto.getStyleClass().add("campo");
        var frecuencia = new SelectorFrecuencia();
        frecuencia.establecer(r.cada(), r.unidad());
        DatePicker inicio = Campos.fecha(r.inicio());
        var avisoInicio = Nodos.etiqueta("Se rehace el historial: se quitan los cobros que ya no caen en el "
                + "nuevo calendario y se registran los que falten hasta hoy.", "leyenda-detalle");
        avisoInicio.setWrapText(true);
        avisoInicio.setMinHeight(Region.USE_PREF_SIZE);
        avisoInicio.visibleProperty().bind(inicio.valueProperty().isNotEqualTo(r.inicio()));
        avisoInicio.managedProperty().bind(avisoInicio.visibleProperty());

        var grupo = new ToggleGroup();
        var chips = new FlowPane(8, 8);
        chips.getChildren().add(chip("Sin categoría", null, grupo, r.categoriaId() == null));
        modelo.resumen().categorias().forEach(c ->
                chips.getChildren().add(chip(c.nombre(), c, grupo, c.id().equals(r.categoriaId()))));
        if (grupo.getSelectedToggle() == null) {
            grupo.getToggles().getFirst().setSelected(true); // su categoría fue eliminada
        }
        // Siempre queda una elegida: un clic sobre la activa no la apaga
        grupo.selectedToggleProperty().addListener((obs, anterior, nuevo) -> {
            if (nuevo == null && anterior != null) {
                anterior.setSelected(true);
            }
        });

        var error = Nodos.etiqueta("", "error");
        error.managedProperty().bind(error.textProperty().isNotEmpty());
        var guardar = Nodos.boton("Guardar cambios", () -> {
            try {
                modelo.editarRecurrencia(r.id(), descripcion.getText(), Formato.leerMonto(monto.getText()),
                        (Categoria) grupo.getSelectedToggle().getUserData(), frecuencia.cada(), frecuencia.unidad(),
                        inicio.getValue());
            } catch (IllegalArgumentException e) {
                error.setText(e.getMessage());
            }
        }, "boton-pildora");

        var montoYDescripcion = new HBox(12, campo("Descripción", descripcion), campo("Monto", monto));
        HBox.setHgrow(montoYDescripcion.getChildren().getFirst(), Priority.ALWAYS);

        return new VBox(16,
                encabezadoSeccion("Editar", "Monto y frecuencia aplican a los próximos cobros"),
                montoYDescripcion,
                campo("Frecuencia", frecuencia),
                new VBox(8, campo("Primer cobro", inicio), avisoInicio),
                campo("Categoría", chips),
                error,
                guardar);
    }

    private Node historial(Recurrencia r, List<Gasto> cobros) {
        BigDecimal total = cobros.stream().map(Gasto::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        var tabla = new VBox();
        tabla.getStyleClass().add("tabla");
        if (cobros.isEmpty()) {
            var vacio = new VBox(Nodos.etiqueta(r.activa()
                    ? "El primer cobro se registrará " + Formato.cuando(r.proximoCobro(modelo.hoy()), modelo.hoy()).toLowerCase() + "."
                    : "Sin cobros registrados.", "vacio-texto"));
            vacio.getStyleClass().add("tabla-vacia");
            tabla.getChildren().add(vacio);
        }
        for (Gasto cobro : cobros) {
            var fecha = Nodos.etiqueta(Formato.dia(cobro.fecha(), modelo.hoy()), "celda");
            var fila = new HBox(fecha, Nodos.espaciador(), Nodos.etiqueta(modelo.monto(cobro), "celda-monto"));
            fila.setAlignment(Pos.CENTER_LEFT);
            fila.getStyleClass().add("tabla-fila");
            fila.setOnMouseClicked(e -> modelo.seleccionadoProperty().set(cobro)); // se edita en el panel
            tabla.getChildren().add(fila);
        }
        String detalle = cobros.isEmpty() ? "" : (cobros.size() == 1 ? "1 cobro" : cobros.size() + " cobros")
                + "  ·  " + Formato.monto(total, modelo.monedaDe(r.cuentaId())) + " en total";
        return new VBox(14, encabezadoSeccion("Cobros registrados", detalle), tabla);
    }

    private List<Node> vacio() {
        var pasos = new VBox(10,
                Nodos.etiqueta("Registra un gasto con «Se repite» activado", "vacio-titulo"),
                Nodos.etiqueta("En el panel de captura, activa «Se repite», elige la frecuencia y la fecha del "
                        + "primer cobro. Cada vez que llegue la fecha, el gasto se registra solo.", "vacio-texto"));
        ((Label) pasos.getChildren().get(1)).setWrapText(true);
        pasos.getStyleClass().add("tabla-vacia");
        var tarjeta = new VBox(pasos);
        tarjeta.getStyleClass().add("tabla");
        return List.of(tarjeta);
    }

    // ---- Utilidades ----

    private static ToggleButton chip(String texto, Categoria categoria, ToggleGroup grupo, boolean elegido) {
        var chip = new ToggleButton(texto, Nodos.punto(Paleta.colorDe(categoria)));
        chip.getStyleClass().add("chip");
        chip.setUserData(categoria);
        chip.setToggleGroup(grupo);
        chip.setSelected(elegido);
        return chip;
    }

    private static Node campo(String etiqueta, Node control) {
        return new VBox(8, Nodos.etiqueta(etiqueta, "campo-etiqueta"), control);
    }

    private static Node encabezadoSeccion(String titulo, String detalle) {
        var fila = new HBox(Nodos.etiqueta(titulo, "seccion-titulo"), Nodos.espaciador(),
                Nodos.etiqueta(detalle, "seccion-detalle"));
        fila.setAlignment(Pos.BASELINE_LEFT);
        return fila;
    }
}
