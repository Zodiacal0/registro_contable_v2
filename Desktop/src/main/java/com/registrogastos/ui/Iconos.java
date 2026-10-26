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
    // Bandera: metas de ahorro
    META("M5 3h2v18H5zM8 4h11l-2.6 4.5L19 13H8z"),
    // Tarjeta: banda superior y cuerpo separados por un hueco (la "banda magnética")
    DEUDA("M3 7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v1H3zM3 10.5h18V17a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2zM6 14.5v2h5v-2z"),
    // Billetera: cuerpo con una muesca a la derecha (el broche) y la solapa arriba
    CUENTA("M6 4.5h11v2H6zM4 8h16v3h-4.5a2 2 0 0 0 0 4H20v4H4zM15.2 12.2h2v1.6h-2z"),
    // Barras que crecen sobre una base: analíticas
    ANALITICAS("M4 19h16v1.6H4zM5 13h3.2v5H5zM10.4 9h3.2v9h-3.2zM15.8 5H19v13h-3.2z"),
    // Libreta de cuentas: hoja con una fila ancha arriba y casillas abajo
    PRESUPUESTO("M6 3h12a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM8 6h8v3H8zM8 11h3v2H8zM13 11h3v2h-3zM8 15h3v2H8zM13 15h3v2h-3z"),
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
