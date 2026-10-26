package com.registrogastos.ui;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;

import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla "esqueleto": bloques con la forma aproximada de la vista real, recorridos por un
 * brillo, que se ven un instante antes de que entre el contenido (efecto de carga de template).
 *
 * El brillo se pinta desde Java con un LinearGradient cuyo centro avanza en cada cuadro: es
 * más barato que reescribir un estilo CSS 60 veces por segundo.
 */
final class Esqueleto extends VBox {

    // Tonos derivados de la paleta (#17151C de fondo): base apenas visible y brillo un paso arriba
    private static final Color BASE = Color.web("#221f29");
    private static final Color BRILLO = Color.web("#312c39");
    private static final double CICLO_MS = 1300;

    private final List<Region> bloques = new ArrayList<>();
    private final List<CornerRadii> radios = new ArrayList<>();

    private final AnimationTimer brillo = new AnimationTimer() {
        @Override
        public void handle(long ahora) {
            // Fase de 0 a 1 que se repite; el centro del brillo viaja de -0.5 a 1.5 (entra y sale)
            double fase = (ahora / 1_000_000.0 % CICLO_MS) / CICLO_MS;
            double centro = -0.5 + fase * 2;
            var degradado = new LinearGradient(centro - 0.45, 0, centro + 0.45, 0, true, CycleMethod.NO_CYCLE,
                    new Stop(0, BASE), new Stop(0.5, BRILLO), new Stop(1, BASE));
            for (int i = 0; i < bloques.size(); i++) {
                bloques.get(i).setBackground(new Background(new BackgroundFill(degradado, radios.get(i), Insets.EMPTY)));
            }
        }
    };

    private Esqueleto() {
        setMouseTransparent(true);
        setVisible(false);
        // En JavaFX un nodo invisible sigue ocupando espacio; oculto, el esqueleto no debe
        // estirar la vista (por ejemplo, alargar el scroll de un mes con pocos gastos)
        managedProperty().bind(visibleProperty());
        getStyleClass().add("esqueleto");
    }

    void iniciar() {
        setOpacity(1);
        setVisible(true);
        brillo.start();
    }

    void detener() {
        brillo.stop();
        setVisible(false);
    }

    // ---- Formas de cada vista ----

    /** Encabezado, hero con barras, reparto por categoría y filas de la tabla. */
    static Esqueleto resumen() {
        var e = new Esqueleto();
        e.getStyleClass().add("contenido");

        var encabezado = new HBox(10, e.bloque(230, 40, 10), Nodos.espaciador(),
                e.bloque(38, 38, 19), e.bloque(38, 38, 19));

        var izquierda = new VBox(12, e.bloque(230, 62, 12), e.bloque(200, 24, 12), e.bloque(140, 14, 7));
        var barras = new HBox(4);
        barras.setAlignment(Pos.BOTTOM_LEFT);
        barras.setMinHeight(128);
        int[] altos = {6, 6, 6, 52, 18, 6, 6, 6, 6, 22, 6, 6, 6, 6, 40, 6, 6, 92, 6, 6, 92, 20, 118, 14, 6, 6, 6, 6, 6, 6, 6};
        for (int alto : altos) {
            barras.getChildren().add(e.bloque(-1, alto, 3));
        }
        var derecha = new VBox(14, e.bloque(260, 12, 6), barras, e.bloque(280, 12, 6));
        HBox.setHgrow(derecha, Priority.ALWAYS);
        var hero = new HBox(56, izquierda, derecha);
        hero.setAlignment(Pos.BOTTOM_LEFT);

        var leyenda = new HBox(16, e.bloque(-1, 40, 12), e.bloque(-1, 40, 12), e.bloque(-1, 40, 12));
        var categorias = new VBox(14, e.bloque(150, 18, 6), e.bloque(-1, 12, 6), leyenda);

        var filas = new VBox(10);
        for (int i = 0; i < 6; i++) {
            filas.getChildren().add(e.bloque(-1, 40, 10));
        }
        var movimientos = new VBox(14,
                new HBox(e.bloque(170, 18, 6), Nodos.espaciador(), e.bloque(240, 34, 17)), filas);

        e.getChildren().addAll(encabezado, hero, categorias, movimientos);
        return e;
    }

    /** Lista de categorías a la izquierda y detalle a la derecha. */
    static Esqueleto categorias() {
        var e = new Esqueleto();

        var lista = new VBox(14, e.bloque(200, 36, 10), e.bloque(150, 14, 7), e.bloque(-1, 40, 12));
        for (int i = 0; i < 6; i++) {
            lista.getChildren().add(e.bloque(-1, 46, 12));
        }
        lista.getStyleClass().add("esqueleto-lista");

        var filas = new VBox(10);
        for (int i = 0; i < 4; i++) {
            filas.getChildren().add(e.bloque(-1, 40, 10));
        }
        var detalle = new VBox(28,
                new VBox(10, e.bloque(240, 40, 10), e.bloque(260, 16, 8)),
                new VBox(14, e.bloque(90, 18, 6), e.bloque(-1, 12, 6), e.bloque(-1, 128, 12)),
                new VBox(14, e.bloque(170, 18, 6), filas));
        detalle.getStyleClass().add("contenido");
        HBox.setHgrow(detalle, Priority.ALWAYS);

        var columnas = new HBox(lista, detalle);
        VBox.setVgrow(columnas, Priority.ALWAYS);
        e.getChildren().add(columnas);
        return e;
    }

    /** Bloque gris redondeado. Ancho -1 = crece para ocupar el espacio disponible. */
    private Region bloque(double ancho, double alto, double radio) {
        var bloque = new Region();
        bloque.setMinHeight(alto);
        bloque.setPrefHeight(alto);
        bloque.setMaxHeight(alto);
        if (ancho < 0) {
            bloque.setMinWidth(4);
            bloque.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(bloque, Priority.ALWAYS);
        } else {
            bloque.setMinWidth(ancho);
            bloque.setPrefWidth(ancho);
            bloque.setMaxWidth(ancho);
        }
        bloques.add(bloque);
        radios.add(new CornerRadii(radio));
        return bloque;
    }
}
