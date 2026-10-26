package com.registrogastos.ui;

import javafx.animation.AnimationTimer;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;

/**
 * Desplazamiento con inercia para un ScrollPane. Por defecto JavaFX salta de golpe con cada
 * paso de la rueda; aquí la rueda solo mueve un "objetivo" y un AnimationTimer acerca la
 * posición real a ese objetivo un porcentaje en cada cuadro, así frena suave al llegar.
 */
final class DesplazamientoSuave {

    /** Píxeles por unidad de la rueda: un paso de rueda en Windows trae ~40, aquí ~90 px. */
    private static final double FACTOR = 2.2;
    /** Fracción de la distancia restante que se recorre por cuadro (60 fps): más alto = más rápido. */
    private static final double SUAVIDAD = 0.18;

    private final ScrollPane scroll;
    private double objetivo;
    private boolean corriendo;

    private final AnimationTimer animador = new AnimationTimer() {
        @Override
        public void handle(long ahora) {
            double actual = scroll.getVvalue();
            double distancia = objetivo - actual;
            if (Math.abs(distancia) < 0.0005) {
                scroll.setVvalue(objetivo);
                stop();
                corriendo = false;
            } else {
                scroll.setVvalue(actual + distancia * SUAVIDAD);
            }
        }
    };

    private DesplazamientoSuave(ScrollPane scroll) {
        this.scroll = scroll;
        // Filtro (no handler): se intercepta antes de que el ScrollPane haga su salto
        scroll.addEventFilter(ScrollEvent.SCROLL, this::alGirarRueda);
    }

    static void aplicar(ScrollPane scroll) {
        new DesplazamientoSuave(scroll);
    }

    private void alGirarRueda(ScrollEvent evento) {
        if (evento.getDeltaY() == 0 || Movimiento.reducido()) {
            return; // desplazamiento horizontal o "reducir movimiento": comportamiento normal
        }
        double recorrible = scroll.getContent().getLayoutBounds().getHeight()
                - scroll.getViewportBounds().getHeight();
        if (recorrible <= 0) {
            return;
        }
        if (!corriendo) {
            objetivo = scroll.getVvalue(); // si se arrastró la barra, partir de donde quedó
        }
        // vvalue va de 0 a 1: los píxeles se convierten a esa escala
        objetivo = Math.clamp(objetivo - evento.getDeltaY() * FACTOR / recorrible, 0, 1);
        corriendo = true;
        animador.start();
        evento.consume();
    }
}
