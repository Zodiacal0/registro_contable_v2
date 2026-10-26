package com.registrogastos.ui;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.Transition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.geometry.Insets;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
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

    /**
     * Curva de desaceleración para lo que ENTRA (secciones, vistas, cifras): arranca con
     * impulso y frena hasta posarse. Antes era "ease-out-expo" (0.16, 1, 0.3, 1), que hace el
     * 60 % del recorrido en el primer 10 % del tiempo: se sentía como un salto y un arrastre.
     */
    static final Interpolator SALIDA = Interpolator.SPLINE(0, 0, 0.2, 1);

    private Movimiento() {
    }

    static boolean reducido() {
        return Platform.getPreferences().isReducedMotion();
    }

    /** Lleva un valor de 0 a 1 en la duración dada; útil para crecer barras o contar cifras. */
    static void progreso(Duration duracion, DoubleConsumer alAvanzar) {
        progreso(duracion, SALIDA, alAvanzar);
    }

    static void progreso(Duration duracion, Interpolator curva, DoubleConsumer alAvanzar) {
        if (reducido()) {
            alAvanzar.accept(1);
            return;
        }
        new Transition() {
            {
                setCycleDuration(duracion);
                setInterpolator(curva);
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

    /**
     * Entrada de una vista: las secciones aparecen en cascada (cada una 40 ms después de la
     * anterior), subiendo 10 px. Empieza en el mismo cuadro: no hay espera, porque los datos
     * ya están (se leen de SQLite en milisegundos). Un esqueleto aquí solo fingiría una carga
     * y haría sentir lenta la app; para cargas reales está {@link #esperar(Esqueleto)}.
     *
     * @return la animación en curso, para poder detenerla si llega otra revelación antes
     */
    static Animation revelar(List<Node> secciones) {
        if (reducido()) {
            secciones.forEach(s -> {
                s.setOpacity(1);
                s.setTranslateY(0);
            });
            return null;
        }
        var todo = new ParallelTransition();
        for (int i = 0; i < secciones.size(); i++) {
            Node seccion = secciones.get(i);
            seccion.setOpacity(0);
            seccion.setTranslateY(10);
            var aparecer = new FadeTransition(Duration.millis(360), seccion);
            aparecer.setToValue(1);
            aparecer.setInterpolator(SALIDA);
            var subir = new TranslateTransition(Duration.millis(480), seccion);
            subir.setToY(0);
            subir.setInterpolator(SALIDA);
            var entrada = new ParallelTransition(aparecer, subir);
            entrada.setDelay(Duration.millis(i * 55));
            todo.getChildren().add(entrada);
        }
        todo.play();
        return todo;
    }

    /**
     * Curva suave al empezar y al terminar (ease-in-out): para algo que se abre en su lugar.
     * La de salida (SALIDA) arranca de golpe y queda bien para lo que entra a la pantalla; un
     * bloque que crece dentro del panel se siente más natural si también acelera de a poco.
     */
    static final Interpolator SUAVE = Interpolator.SPLINE(0.4, 0, 0.2, 1);

    /**
     * Muestra u oculta {@code bloque} según {@code abierto}, desplegándolo en vez de hacerlo
     * aparecer de golpe. Deja el estado inicial puesto sin animar.
     */
    static void desplegarCon(Region bloque, ObservableValue<Boolean> abierto) {
        boolean inicial = Boolean.TRUE.equals(abierto.getValue());
        bloque.setVisible(inicial);
        bloque.setManaged(inicial);
        abierto.addListener((obs, antes, ahora) -> desplegar(bloque, Boolean.TRUE.equals(ahora)));
    }

    /**
     * Abre o cierra un bloque animando su alto de 0 a su alto natural (o al revés), con un
     * recorte para que el contenido no se salga mientras crece, un fundido y un desliz de 6 px.
     *
     * El alto natural se pide con prefHeight(ancho): un FlowPane de chips mide distinto según
     * el ancho disponible, así que se calcula con el ancho real del contenedor.
     */
    static void desplegar(Region bloque, boolean abrir) {
        if (bloque.getProperties().get("desplegando") instanceof Animation anterior) {
            anterior.stop(); // un clic rápido de ida y vuelta: se parte desde donde iba
        }
        if (abrir == bloque.isVisible() && bloque.getClip() == null) {
            return;
        }
        if (reducido() || bloque.getScene() == null) {
            // Sin pantalla todavía (p. ej. el formulario se está armando): directo al estado final
            bloque.setVisible(abrir);
            bloque.setManaged(abrir);
            return;
        }
        // El VBox mete su espaciado apenas el bloque ocupa lugar: se compensa con un margen
        // negativo que se anima junto con el alto, así no hay un salto al empezar ni al terminar
        double espacio = bloque.getParent() instanceof VBox caja ? caja.getSpacing() : 0;
        double ancho = bloque.getParent() instanceof Region padre
                ? padre.getWidth() - padre.snappedLeftInset() - padre.snappedRightInset()
                : bloque.getWidth();
        double desde = bloque.isVisible() ? bloque.getHeight() : 0;
        bloque.setVisible(true);
        bloque.setManaged(true);
        bloque.applyCss(); // el texto que se acaba de poner necesita su fuente para medirse
        double hasta = abrir ? bloque.prefHeight(ancho) : 0;
        // El estado del primer cuadro se fija YA: si se esperara al primer interpolate(), un
        // layout intermedio podría mostrar el bloque entero por un instante
        bloque.setPrefHeight(desde);
        bloque.setMaxHeight(desde);
        bloque.setOpacity(abrir && desde == 0 ? 0 : bloque.getOpacity());
        var recorte = new Rectangle();
        recorte.widthProperty().bind(bloque.widthProperty());
        recorte.heightProperty().bind(bloque.heightProperty());
        bloque.setClip(recorte);
        bloque.setMinHeight(0);
        double opacidadInicial = bloque.getOpacity();

        var animacion = new Transition() {
            {
                setCycleDuration(Duration.millis(abrir ? 340 : 260));
                setInterpolator(SUAVE);
            }

            @Override
            protected void interpolate(double f) {
                double alto = desde + (hasta - desde) * f;
                bloque.setPrefHeight(alto);
                bloque.setMaxHeight(alto);
                double abierto = Math.max(desde, hasta) > 0 ? alto / Math.max(desde, hasta) : 0;
                VBox.setMargin(bloque, new Insets(-espacio * (1 - abierto), 0, 0, 0));
                bloque.setOpacity(abrir ? opacidadInicial + (1 - opacidadInicial) * f : opacidadInicial * (1 - f));
                bloque.setTranslateY(abrir ? -6 * (1 - f) : -6 * f);
            }
        };
        animacion.setOnFinished(e -> {
            // Al terminar, el bloque vuelve a medirse solo (si cambia su contenido, se ajusta)
            bloque.setClip(null);
            bloque.setMinHeight(Region.USE_COMPUTED_SIZE);
            bloque.setPrefHeight(Region.USE_COMPUTED_SIZE);
            bloque.setMaxHeight(Region.USE_COMPUTED_SIZE);
            bloque.setTranslateY(0);
            bloque.setOpacity(1);
            VBox.setMargin(bloque, null);
            bloque.setVisible(abrir);
            bloque.setManaged(abrir);
            bloque.getProperties().remove("desplegando");
        });
        bloque.getProperties().put("desplegando", animacion);
        animacion.play();
    }

    /** La perilla del interruptor se desliza a su lado (en vez de saltar por CSS). */
    static void deslizarPerilla(Node perilla, boolean encendido, double recorrido) {
        if (reducido() || perilla.getScene() == null) {
            perilla.setTranslateX(encendido ? recorrido : 0);
            return;
        }
        var deslizar = new TranslateTransition(Duration.millis(220), perilla);
        deslizar.setToX(encendido ? recorrido : 0);
        deslizar.setInterpolator(SUAVE);
        deslizar.play();
    }

    /** Si una carga tarda menos que esto, el esqueleto nunca llega a verse. */
    static final Duration UMBRAL_ESQUELETO = Duration.millis(200);

    /**
     * Para una carga en segundo plano: el esqueleto aparece SOLO si la carga pasa de
     * {@link #UMBRAL_ESQUELETO}. Llama a {@link Espera#listo()} cuando lleguen los datos.
     */
    static Espera esperar(Esqueleto esqueleto) {
        return new Espera(esqueleto);
    }

    static final class Espera {
        private final Esqueleto esqueleto;
        private final PauseTransition umbral = new PauseTransition(UMBRAL_ESQUELETO);

        private Espera(Esqueleto esqueleto) {
            this.esqueleto = esqueleto;
            umbral.setOnFinished(e -> {
                esqueleto.setOpacity(1);
                esqueleto.iniciar();
            });
            umbral.play();
        }

        /** Los datos llegaron: si el esqueleto no alcanzó a salir, ya no sale; si salió, se desvanece. */
        void listo() {
            umbral.stop();
            if (!esqueleto.isVisible()) {
                return;
            }
            if (reducido()) {
                esqueleto.detener();
                return;
            }
            var salida = new FadeTransition(Duration.millis(180), esqueleto);
            salida.setToValue(0);
            salida.setOnFinished(e -> esqueleto.detener());
            salida.play();
        }
    }

    /** Entrada de una vista: aparece y se desliza 14 px desde la derecha. */
    static void entrar(Node vista) {
        if (reducido()) {
            return;
        }
        var fundido = new FadeTransition(Duration.millis(300), vista);
        fundido.setFromValue(0);
        fundido.setToValue(1);
        fundido.setInterpolator(SALIDA);
        var deslizar = new TranslateTransition(Duration.millis(420), vista);
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
