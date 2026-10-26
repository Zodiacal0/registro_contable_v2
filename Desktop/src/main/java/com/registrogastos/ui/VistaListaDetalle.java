package com.registrogastos.ui;

import javafx.animation.Animation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;

/**
 * Esqueleto común de las vistas "lista + detalle" (biblioteca de Steam): columna izquierda con
 * título, subtítulo, una acción opcional y la lista; a la derecha, el detalle con scroll.
 * Las subclases solo implementan {@link #pintar()}.
 *
 * Ojo con el orden de construcción: el constructor de esta clase corre ANTES de que la
 * subclase inicialice sus propios campos. Por eso aquí no se llama a pintar(): si lo hiciera,
 * la subclase pintaría con sus campos todavía en null. Cada subclase lo llama al final de su
 * constructor.
 */
abstract class VistaListaDetalle extends StackPane implements Revelable {

    protected final ModeloUi modelo;
    protected final VBox lista = new VBox(2);
    protected final VBox detalle = new VBox(32);
    protected final Label subtitulo = Nodos.etiqueta("", "subtitulo");

    private final VBox columnaLista;
    private final VBox accion = new VBox();
    private Animation revelando;

    protected VistaListaDetalle(ModeloUi modelo, String titulo) {
        this.modelo = modelo;

        var desplazable = new ScrollPane(lista);
        desplazable.setFitToWidth(true);
        desplazable.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        desplazable.getStyleClass().add("vista");
        DesplazamientoSuave.aplicar(desplazable);
        VBox.setVgrow(desplazable, Priority.ALWAYS);

        accion.managedProperty().bind(accion.visibleProperty());
        accion.setVisible(false);
        columnaLista = new VBox(18, new VBox(4, Nodos.etiqueta(titulo, "titulo"), subtitulo), accion, desplazable);
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
    }

    /** Repinta lista y detalle con el estado actual del modelo. */
    protected abstract void pintar();

    /** Controles entre el título y la lista (p. ej. "+ Nueva meta"). */
    protected void ponerAccion(Node... nodos) {
        for (Node nodo : nodos) {
            if (nodo instanceof Region region) {
                region.setMaxWidth(Double.MAX_VALUE); // ocupan el ancho de la columna
            }
        }
        accion.getChildren().setAll(nodos);
        accion.setSpacing(10);
        accion.setVisible(true);
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

    // ---- Piezas comunes ----

    protected static Node encabezadoSeccion(String titulo, String detalle) {
        var fila = new HBox(Nodos.etiqueta(titulo, "seccion-titulo"), Nodos.espaciador(),
                Nodos.etiqueta(detalle, "seccion-detalle"));
        fila.setAlignment(Pos.BASELINE_LEFT);
        return fila;
    }

    protected static Region muestra(String color) {
        var muestra = new Region();
        muestra.getStyleClass().add("muestra");
        muestra.setStyle("-fx-background-color: " + color + ";");
        return muestra;
    }

    /** Barra de progreso: una pista y un relleno cuyo ancho es pista * proporción (topada en 1). */
    protected static Node barraProgreso(double proporcion, String color, boolean gruesa) {
        var pista = new StackPane();
        pista.getStyleClass().addAll("progreso-pista", gruesa ? "progreso-gruesa" : "progreso-fina");
        var relleno = new Region();
        relleno.getStyleClass().add("progreso-relleno");
        relleno.setStyle("-fx-background-color: " + color + ";");
        relleno.maxWidthProperty().bind(pista.widthProperty().multiply(Math.min(1, Math.max(0, proporcion))));
        pista.getChildren().add(relleno);
        StackPane.setAlignment(relleno, Pos.CENTER_LEFT);
        return pista;
    }

    /** Dato corto en píldora (p. ej. "Faltan 500.00"). */
    protected static Label dato(String texto) {
        return Nodos.etiqueta(texto, "pildora-dato");
    }

    /** Ítem de la lista izquierda con su estado de elegido. */
    protected static HBox itemLista(Node contenido, boolean elegido, Runnable alElegir) {
        var item = new HBox(contenido);
        HBox.setHgrow(contenido, Priority.ALWAYS);
        item.getStyleClass().add("lista-item");
        if (elegido) {
            item.getStyleClass().add("lista-item-elegido");
        }
        item.setOnMouseClicked(e -> alElegir.run());
        return item;
    }

    /** Fila de historial: fecha a la izquierda, monto a la derecha y un botón para quitarla. */
    protected Node filaHistorial(String fecha, String monto, Runnable quitar) {
        var borrar = Iconos.CERRAR.boton("Quitar", quitar);
        borrar.getStyleClass().addAll("boton-icono-chico", "fila-quitar");
        var fila = new HBox(12, Nodos.etiqueta(fecha, "celda"), Nodos.espaciador(),
                Nodos.etiqueta(monto, "celda-monto"), borrar);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().add("tabla-fila");
        return fila;
    }

    protected static VBox tabla(Node... filas) {
        var tabla = new VBox(filas);
        tabla.getStyleClass().add("tabla");
        return tabla;
    }

    /** Botón de borrado en dos pasos: el primer clic lo arma, el segundo ejecuta. */
    protected static Button botonConfirmar(String texto, String confirmar, Runnable accion) {
        var boton = new Button(texto);
        boton.getStyleClass().addAll("boton", "boton-texto");
        boton.setOnAction(e -> {
            if (!boton.getStyleClass().contains("boton-texto-confirmar")) {
                boton.setText(confirmar);
                boton.getStyleClass().add("boton-texto-confirmar");
                return;
            }
            accion.run();
        });
        return boton;
    }

    /** Corre una acción de formulario; si el dominio la rechaza, muestra el motivo en la etiqueta. */
    protected static void ejecutar(Label error, Runnable accion) {
        try {
            error.setText("");
            accion.run();
        } catch (IllegalArgumentException e) {
            error.setText(e.getMessage());
        }
    }

    protected static Label etiquetaError() {
        var error = Nodos.etiqueta("", "error");
        error.managedProperty().bind(error.textProperty().isNotEmpty());
        return error;
    }

    protected static Node vacio(String texto) {
        var etiqueta = Nodos.etiqueta(texto, "vacio-texto");
        etiqueta.setWrapText(true);
        var caja = new VBox(etiqueta);
        caja.getStyleClass().add("tabla-vacia");
        return tabla(caja);
    }
}
