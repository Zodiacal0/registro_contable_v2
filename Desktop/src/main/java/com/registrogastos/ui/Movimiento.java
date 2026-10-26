package com.registrogastos.ui;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.Transition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.DoubleConsumer;

/**
 * Las animaciones de la app, en un solo lugar. Todas usan salida exponencial (rápido al
 * inicio, suave al final) y se apagan si Windows tiene activado "reducir movimiento".
 */
final class Movimiento {

    /** Curva de salida tipo "ease-out-expo": casi todo el recorrido ocurre al principio. */
    static final Interpolator SALIDA = Interpolator.SPLINE(0.16, 1, 0.3, 1);

    private Movimiento() {
    }

    static boolean reducido() {
        return Platform.getPreferences().isReducedMotion();
    }

    /** Lleva un valor de 0 a 1 en la duración dada; útil para crecer barras o contar cifras. */
    static void progreso(Duration duracion, DoubleConsumer alAvanzar) {
        if (reducido()) {
            alAvanzar.accept(1);
            return;
        }
        new Transition() {
            {
                setCycleDuration(duracion);
                setInterpolator(SALIDA);
            }

            @Override
            protected void interpolate(double fraccion) {
                alAvanzar.accept(fraccion);
            }
        }.play();
    }

    /** El saldo "cuenta" desde el valor anterior hasta el nuevo, partido en entero y decimales. */
    static void contar(Label entero, Label decimales, BigDecimal desde, BigDecimal hasta) {
        progreso(Duration.millis(650), f -> {
            BigDecimal actual = desde.add(hasta.subtract(desde).multiply(BigDecimal.valueOf(f)))
                    .setScale(2, RoundingMode.HALF_UP);
            String[] partes = Formato.montoPartido(actual);
            entero.setText(partes[0]);
            decimales.setText(partes[1]);
        });
    }

    /** Momento de la última revelación: si se navega rápido, se salta el esqueleto. */
    private static long ultimaRevelacion;

    /**
     * Carga tipo "template": el esqueleto brilla un instante y luego las secciones entran en
     * cascada (cada una 70 ms después de la anterior), subiendo 14 px mientras aparecen.
     *
     * Si la revelación anterior fue hace menos de 700 ms (por ejemplo, pasando meses rápido)
     * se salta el esqueleto: la cascada sola ya da la sensación de cambio sin hacer esperar.
     *
     * @return la animación en curso, para poder detenerla si llega otra revelación antes
     */
    static Animation revelar(Esqueleto esqueleto, List<Node> secciones) {
        esqueleto.detener();
        if (reducido()) {
            secciones.forEach(s -> {
                s.setOpacity(1);
                s.setTranslateY(0);
            });
            return null;
        }
        long ahora = System.currentTimeMillis();
        boolean rapido = ahora - ultimaRevelacion < 700;
        ultimaRevelacion = ahora;

        var todo = new ParallelTransition();
        Duration inicio = Duration.ZERO;
        if (!rapido) {
            inicio = Duration.millis(340);
            esqueleto.iniciar();
            var salida = new FadeTransition(Duration.millis(220), esqueleto);
            salida.setFromValue(1);
            salida.setToValue(0);
            salida.setDelay(inicio);
            salida.setOnFinished(e -> esqueleto.detener());
            todo.getChildren().add(salida);
        }
        for (int i = 0; i < secciones.size(); i++) {
            Node seccion = secciones.get(i);
            seccion.setOpacity(0);
            seccion.setTranslateY(14);
            var aparecer = new FadeTransition(Duration.millis(380), seccion);
            aparecer.setToValue(1);
            var subir = new TranslateTransition(Duration.millis(520), seccion);
            subir.setToY(0);
            subir.setInterpolator(SALIDA);
            var entrada = new ParallelTransition(aparecer, subir);
            entrada.setDelay(inicio.add(Duration.millis(i * 70)));
            todo.getChildren().add(entrada);
        }
        todo.play();
        return todo;
    }

    /** Entrada de una vista: aparece y se desliza 14 px desde la derecha. */
    static void entrar(Node vista) {
        if (reducido()) {
            return;
        }
        var fundido = new FadeTransition(Duration.millis(220), vista);
        fundido.setFromValue(0);
        fundido.setToValue(1);
        var deslizar = new TranslateTransition(Duration.millis(320), vista);
        deslizar.setFromX(14);
        deslizar.setToX(0);
        deslizar.setInterpolator(SALIDA);
        new ParallelTransition(fundido, deslizar).play();
    }

    /** Destello que se apaga despacio: marca dónde quedó lo que se acaba de guardar. */
    static void resaltar(Node resaltado) {
        if (reducido()) {
            resaltado.setOpacity(0);
            return;
        }
        var fundido = new FadeTransition(Duration.millis(1600), resaltado);
        fundido.setFromValue(1);
        fundido.setToValue(0);
        fundido.setInterpolator(Interpolator.EASE_IN);
        fundido.setDelay(Duration.millis(250));
        resaltado.setOpacity(1);
        fundido.play();
    }
}
