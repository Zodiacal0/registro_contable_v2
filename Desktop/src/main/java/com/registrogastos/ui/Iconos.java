package com.registrogastos.ui;

import javafx.scene.control.Button;
import javafx.scene.layout.Region;

/**
 * Íconos dibujados como trazos SVG sobre una Region: JavaFX rellena la forma con
 * -fx-background-color y la escala al tamaño de la Region (-fx-shape en estilos.css).
 * Todos se diseñaron en una grilla de 24x24 con el mismo grosor visual.
 */
enum Iconos {
    RESUMEN("M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z"),
    CATEGORIAS("M5 4h14a2 2 0 0 1 0 4H5a2 2 0 0 1 0-4zM5 10h10a2 2 0 0 1 0 4H5a2 2 0 0 1 0-4zM5 16h6a2 2 0 0 1 0 4H5a2 2 0 0 1 0-4z"),
    ANTERIOR("M15.4 4.6 17 6.2 11.2 12 17 17.8 15.4 19.4 8 12z"),
    SIGUIENTE("M8.6 4.6 7 6.2 12.8 12 7 17.8 8.6 19.4 16 12z"),
    MAS("M11 4h2v7h7v2h-7v7h-2v-7H4v-2h7z"),
    // Flechas en círculo: ícono "sync" de Material Icons (Google, licencia Apache 2.0)
    REPETIR("M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46C19.54 15.03 20 13.57 20 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74C4.46 8.97 4 10.43 4 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z"),
    OCULTAR_PANEL("M5.6 4.6 4 6.2 9.8 12 4 17.8 5.6 19.4 13 12zM12.6 4.6 11 6.2 16.8 12 11 17.8 12.6 19.4 20 12z"),
    SUBE("M12 6 19 17H5z"),
    BAJA("M5 7h14l-7 11z"),
    CERRAR("M6.4 5 12 10.6 17.6 5 19 6.4 13.4 12 19 17.6 17.6 19 12 13.4 6.4 19 5 17.6 10.6 12 5 6.4z");

    private final String trazo;

    Iconos(String trazo) {
        this.trazo = trazo;
    }

    /** Region con la forma del ícono; el color y tamaño los pone el CSS (clase "icono"). */
    Region crear(String... clases) {
        var region = new Region();
        region.getStyleClass().add("icono");
        region.getStyleClass().addAll(clases);
        region.setStyle("-fx-shape: \"" + trazo + "\";");
        return region;
    }

    /** Botón redondo que solo muestra el ícono; el texto queda como ayuda accesible. */
    Button boton(String descripcion, Runnable accion) {
        var boton = new Button();
        boton.setGraphic(crear());
        boton.getStyleClass().addAll("boton", "boton-icono");
        boton.setAccessibleText(descripcion);
        boton.setOnAction(e -> accion.run());
        return boton;
    }
}
