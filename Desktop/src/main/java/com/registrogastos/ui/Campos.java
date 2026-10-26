package com.registrogastos.ui;

import javafx.scene.Node;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Controles de formulario con el estilo de la app, para no repetir su configuración en cada vista. */
final class Campos {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Campos() {
    }

    static TextField texto(String valor, String ejemplo) {
        var campo = new TextField(valor);
        campo.setPromptText(ejemplo);
        campo.getStyleClass().add("campo");
        return campo;
    }

    static TextField monto(BigDecimal valor) {
        return texto(valor == null ? "" : valor.toPlainString(), "0.00");
    }

    /** Solo se elige del calendario (no se escribe): así no hay fechas mal tecleadas. */
    static DatePicker fecha(LocalDate valor) {
        var fecha = new DatePicker(valor);
        fecha.setEditable(false);
        fecha.setMaxWidth(Double.MAX_VALUE);
        fecha.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate valor) {
                return valor == null ? "" : valor.format(FECHA);
            }

            @Override
            public LocalDate fromString(String texto) {
                return texto == null || texto.isBlank() ? null : LocalDate.parse(texto, FECHA);
            }
        });
        return fecha;
    }

    static Node etiquetado(String etiqueta, Node control) {
        return new VBox(8, Nodos.etiqueta(etiqueta, "campo-etiqueta"), control);
    }

    /** Interruptor tipo iOS (estilo .interruptor en estilos.css). */
    static ToggleButton interruptor(String descripcion) {
        var perilla = new Region();
        perilla.getStyleClass().add("interruptor-perilla");
        var interruptor = new ToggleButton();
        interruptor.setGraphic(perilla);
        interruptor.getStyleClass().add("interruptor");
        interruptor.setAccessibleText(descripcion);
        return interruptor;
    }
}
