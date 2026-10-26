package com.registrogastos.ui;

import com.registrogastos.dominio.Recurrencia;
import javafx.geometry.Pos;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Elige la frecuencia de una recurrencia: Mensual, Anual o Personalizada ("Cada N días/meses").
 * Se usa en el panel de captura y en el detalle de cada recurrente.
 */
final class SelectorFrecuencia extends VBox {

    private final ToggleGroup tipos = new ToggleGroup();
    private final ToggleButton mensual = chip("Mensual", tipos);
    private final ToggleButton anual = chip("Anual", tipos);
    private final ToggleButton personalizada = chip("Personalizada", tipos);

    private final ToggleGroup unidades = new ToggleGroup();
    private final ToggleButton dias = chip("días", unidades);
    private final ToggleButton meses = chip("meses", unidades);
    private final TextField cantidad = new TextField("2");

    SelectorFrecuencia() {
        super(10);
        cantidad.getStyleClass().addAll("campo", "campo-cantidad");
        cantidad.setPrefColumnCount(3);

        var detalle = new HBox(8, Nodos.etiqueta("Cada", "campo-etiqueta"), cantidad, dias, meses);
        detalle.setAlignment(Pos.CENTER_LEFT);
        detalle.visibleProperty().bind(personalizada.selectedProperty());
        detalle.managedProperty().bind(detalle.visibleProperty());

        getChildren().addAll(new HBox(8, mensual, anual, personalizada), detalle);
        mantenerUnoElegido(tipos);
        mantenerUnoElegido(unidades);
        establecer(1, Recurrencia.Unidad.MESES);
    }

    /** Selecciona la opción que corresponde a "cada N unidades". */
    void establecer(int cada, Recurrencia.Unidad unidad) {
        if (unidad == Recurrencia.Unidad.MESES && cada == 1) {
            mensual.setSelected(true);
        } else if (unidad == Recurrencia.Unidad.MESES && cada == 12) {
            anual.setSelected(true);
        } else {
            personalizada.setSelected(true);
            cantidad.setText(String.valueOf(cada));
            (unidad == Recurrencia.Unidad.DIAS ? dias : meses).setSelected(true);
            return;
        }
        meses.setSelected(true);
    }

    int cada() {
        if (mensual.isSelected()) {
            return 1;
        }
        if (anual.isSelected()) {
            return 12;
        }
        try {
            int n = Integer.parseInt(cantidad.getText().strip());
            if (n > 0) {
                return n;
            }
        } catch (NumberFormatException e) {
            // cae al mensaje de abajo
        }
        throw new IllegalArgumentException("La frecuencia lleva un número entero, por ejemplo cada 3 meses");
    }

    Recurrencia.Unidad unidad() {
        return personalizada.isSelected() && dias.isSelected() ? Recurrencia.Unidad.DIAS : Recurrencia.Unidad.MESES;
    }

    private static ToggleButton chip(String texto, ToggleGroup grupo) {
        var chip = new ToggleButton(texto);
        chip.getStyleClass().add("chip");
        chip.setToggleGroup(grupo);
        return chip;
    }

    /** Un clic sobre la opción activa no la apaga: siempre queda una elegida. */
    private static void mantenerUnoElegido(ToggleGroup grupo) {
        grupo.selectedToggleProperty().addListener((obs, anterior, nuevo) -> {
            if (nuevo == null && anterior != null) {
                anterior.setSelected(true);
            }
        });
    }
}
