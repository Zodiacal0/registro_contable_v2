package com.registrogastos.ui;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/** Atajos para crear nodos con sus clases CSS. El aspecto vive en estilos.css, no aquí. */
final class Nodos {

    private Nodos() {
    }

    static Label etiqueta(String texto, String... clases) {
        var label = new Label(texto);
        label.getStyleClass().addAll(clases);
        return label;
    }

    static Button boton(String texto, Runnable accion, String... clases) {
        var boton = new Button(texto);
        boton.getStyleClass().add("boton");
        boton.getStyleClass().addAll(clases);
        boton.setOnAction(e -> accion.run());
        return boton;
    }

    /** Región que se estira y empuja a los vecinos hacia los extremos de un HBox. */
    static Region espaciador() {
        var region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    static Region punto(String color) {
        var punto = new Region();
        punto.getStyleClass().add("punto");
        punto.setStyle("-fx-background-color: " + color + ";");
        return punto;
    }
}
