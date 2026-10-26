package com.registrogastos.ui;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Barras de gasto por día del mes (como el resumen de Apple Wallet) con una línea
 * punteada en el promedio diario. Hecha con Regions en un GridPane: una columna por día,
 * todas del mismo ancho porcentual, así barras y etiquetas quedan alineadas solas.
 *
 * Interacción (si se pasa {@code alElegir}): al pasar el mouse la columna se resalta y
 * {@link #lecturaProperty()} describe ese día (como la mira de Binance); el clic avisa qué
 * día se eligió.
 */
final class GraficaDias extends StackPane {

    private static final double ALTO = 128;
    private static final double ALTO_VACIO = 3;

    private final ReadOnlyStringWrapper lectura = new ReadOnlyStringWrapper("");
    private final List<StackPane> celdas = new ArrayList<>();
    private final YearMonth mes;

    /**
     * @param animar   true = las barras crecen desde abajo (al entrar a un mes)
     * @param alElegir qué hacer al hacer clic en un día; null = gráfica solo de lectura
     */
    GraficaDias(YearMonth mes, List<BigDecimal> porDia, BigDecimal promedio, LocalDate hoy,
                boolean animar, Consumer<LocalDate> alElegir) {
        this.mes = mes;
        getStyleClass().add("grafica");
        var grilla = new GridPane();
        grilla.setHgap(4);
        grilla.setVgap(10);

        int dias = porDia.size();
        for (int i = 0; i < dias; i++) {
            var columna = new ColumnConstraints();
            columna.setPercentWidth(100.0 / dias);
            columna.setHalignment(HPos.CENTER);
            grilla.getColumnConstraints().add(columna);
        }

        BigDecimal maximo = porDia.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        int pico = maximo.signum() > 0 ? porDia.indexOf(maximo) : -1;
        var rellenos = new ArrayList<Region>();
        var altos = new ArrayList<Double>();
        for (int i = 0; i < dias; i++) {
            LocalDate fecha = mes.atDay(i + 1);
            BigDecimal valor = porDia.get(i);
            var relleno = new Region();
            double alto = estilizar(relleno, valor, maximo, fecha, hoy, i == pico);
            relleno.setPrefHeight(alto);
            rellenos.add(relleno);
            altos.add(alto);

            var celda = celda(relleno, fecha, valor, hoy, alElegir);
            celdas.add(celda);
            grilla.add(celda, i, 0);
            if (i % 7 == 0) {
                var etiqueta = Nodos.etiqueta(String.valueOf(i + 1), "grafica-eje");
                etiqueta.setMinWidth(Region.USE_PREF_SIZE); // la columna es angosta: que no se recorte a "…"
                GridPane.setHalignment(etiqueta, HPos.LEFT);
                grilla.add(etiqueta, i, 1);
            }
        }
        getChildren().add(grilla);

        // Línea de promedio: misma escala que las barras, anclada abajo y desplazada hacia arriba
        if (maximo.signum() > 0 && promedio.signum() > 0) {
            double alto = ALTO * promedio.divide(maximo, 4, RoundingMode.HALF_UP).doubleValue();
            var linea = new Region();
            linea.getStyleClass().add("grafica-promedio");
            linea.setManaged(false); // fuera del layout del StackPane: se posiciona a mano
            linea.setMouseTransparent(true);
            linea.setLayoutY(ALTO - alto);
            widthProperty().addListener((obs, a, ancho) -> linea.resize(ancho.doubleValue(), 2));
            getChildren().add(linea);
        }

        if (animar) {
            // Todas las barras crecen juntas desde su mínimo hasta su alto real
            Movimiento.progreso(Duration.millis(700), f -> {
                for (int i = 0; i < rellenos.size(); i++) {
                    double destino = altos.get(i);
                    double inicio = Math.min(destino, ALTO_VACIO);
                    rellenos.get(i).setPrefHeight(inicio + (destino - inicio) * f);
                }
            });
        }
    }

    /** Lo que dice la gráfica del día bajo el mouse; vacío si el mouse no está encima. */
    ReadOnlyStringProperty lecturaProperty() {
        return lectura.getReadOnlyProperty();
    }

    /** Marca la columna del día elegido (o ninguna con null). */
    void marcarDia(LocalDate dia) {
        for (int i = 0; i < celdas.size(); i++) {
            celdas.get(i).getStyleClass().remove("grafica-celda-elegida");
            if (dia != null && YearMonth.from(dia).equals(mes) && dia.getDayOfMonth() == i + 1) {
                celdas.get(i).getStyleClass().add("grafica-celda-elegida");
            }
        }
    }

    /** Aplica la clase CSS según el tipo de día y devuelve el alto que le corresponde. */
    private static double estilizar(Region relleno, BigDecimal valor, BigDecimal maximo,
                                    LocalDate fecha, LocalDate hoy, boolean pico) {
        relleno.setMaxWidth(Double.MAX_VALUE);
        relleno.setMaxHeight(Region.USE_PREF_SIZE);
        relleno.setMouseTransparent(true); // el mouse lo recibe la celda completa
        if (valor.signum() == 0 || maximo.signum() == 0) {
            relleno.getStyleClass().add(fecha.isAfter(hoy) ? "barra-dia-futuro" : "barra-dia-vacio");
            return ALTO_VACIO;
        }
        // Hoy en el tono más claro; el día de mayor gasto, un paso por debajo
        relleno.getStyleClass().add(fecha.equals(hoy) ? "barra-dia-hoy" : pico ? "barra-dia-pico" : "barra-dia");
        return Math.max(4, ALTO * valor.divide(maximo, 4, RoundingMode.HALF_UP).doubleValue());
    }

    /** La celda ocupa toda la columna: así el mouse "acierta" al día aunque la barra sea baja. */
    private StackPane celda(Region relleno, LocalDate fecha, BigDecimal valor, LocalDate hoy,
                            Consumer<LocalDate> alElegir) {
        var celda = new StackPane(relleno);
        celda.getStyleClass().add("grafica-celda");
        celda.setAlignment(Pos.BOTTOM_CENTER);
        celda.setMinHeight(ALTO);
        celda.setPrefHeight(ALTO);
        GridPane.setValignment(celda, VPos.BOTTOM);
        GridPane.setHgrow(celda, Priority.ALWAYS);

        celda.setOnMouseEntered(e -> lectura.set(Formato.dia(fecha, hoy) + "  ·  "
                + (valor.signum() == 0 ? "sin gastos" : Formato.monto(valor))));
        celda.setOnMouseExited(e -> lectura.set(""));
        if (alElegir != null) {
            celda.setOnMouseClicked(e -> alElegir.accept(fecha));
        }
        return celda;
    }

    /** Leyenda bajo la gráfica: el día de mayor gasto (el que se marca) y el promedio. */
    static HBox leyenda(YearMonth mes, List<BigDecimal> porDia, BigDecimal promedio, LocalDate hoy) {
        var fila = new HBox(20);
        BigDecimal maximo = porDia.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        if (maximo.signum() > 0) {
            LocalDate dia = mes.atDay(porDia.indexOf(maximo) + 1);
            fila.getChildren().add(muestra("grafica-muestra-pico",
                    "Día más alto  " + Formato.fechaCorta(dia, hoy) + "  ·  " + Formato.monto(maximo)));
        }
        fila.getChildren().add(muestra("grafica-muestra-promedio", "Promedio diario  " + Formato.monto(promedio)));
        return fila;
    }

    private static HBox muestra(String clase, String texto) {
        var marca = new Region();
        marca.getStyleClass().add(clase);
        var item = new HBox(8, marca, Nodos.etiqueta(texto, "grafica-leyenda"));
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }
}
