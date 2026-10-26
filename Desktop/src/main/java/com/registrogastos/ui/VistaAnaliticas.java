package com.registrogastos.ui;

import com.registrogastos.servicio.Analisis;
import javafx.animation.Animation;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

/**
 * Analíticas: qué compras más seguido, los gastos hormiga, las categorías que subieron, lo que
 * se va en recurrentes y cuánto podrías ahorrar (y qué tan antes llegarías a una meta).
 *
 * El análisis recorre 120 días de gastos, así que corre FUERA del hilo de la interfaz
 * (CompletableFuture) y vuelve con Platform.runLater. Mientras tanto no se muestra nada; el
 * esqueleto solo aparece si tarda más de {@link Movimiento#UMBRAL_ESQUELETO}.
 */
final class VistaAnaliticas extends ScrollPane implements Revelable {

    private static final String COLOR_BARRA = "#aaa2b3";
    /**
     * Las dos series de las gráficas de líneas: la principal casi blanca y la de comparación
     * violeta medio. Validado contra el fondo #17151c: ΔE 30 entre ambas (también con
     * daltonismo) y contraste mayor a 3:1. Además del color, las separa el trazo: continuo
     * contra discontinuo.
     */
    private static final String COLOR_PRINCIPAL = "#e2dde7";
    private static final String COLOR_COMPARACION = "#8a7aa8";
    private static final Locale ES = Locale.of("es");

    private final ModeloUi modelo;
    private final VBox contenido = new VBox(48);
    private final Esqueleto esqueleto = Esqueleto.resumen();
    private Animation revelando;
    /** Cuál fue el último análisis pedido: si llega uno viejo (se pidió otro después), se descarta. */
    private long pedido;
    /** Cambiaron los datos mientras la vista no estaba en pantalla: recalcular al entrar. */
    private boolean desactualizada = true;

    VistaAnaliticas(ModeloUi modelo) {
        this.modelo = modelo;
        contenido.getStyleClass().add("contenido");
        var capas = new StackPane(contenido, esqueleto);
        capas.setAlignment(Pos.TOP_CENTER);
        setContent(capas);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        getStyleClass().add("vista");
        DesplazamientoSuave.aplicar(this);

        modelo.resumenProperty().addListener((obs, anterior, resumen) -> {
            if (getScene() != null) {
                analizar(false); // en pantalla: se actualiza sin volver a hacer la entrada
            } else {
                desactualizada = true;
            }
        });
    }

    @Override
    public void revelar() {
        analizar(true);
    }

    private void analizar(boolean conEntrada) {
        long este = ++pedido;
        var espera = Movimiento.esperar(esqueleto);
        if (!desactualizada && !contenido.getChildren().isEmpty() && conEntrada) {
            // Ya estaba calculado: se muestra lo que hay mientras se recalcula por si acaso
            entrar();
        }
        CompletableFuture.supplyAsync(modelo::analizar).whenComplete((analisis, error) -> Platform.runLater(() -> {
            if (este != pedido) {
                return; // llegó tarde: hay un pedido más nuevo en curso
            }
            espera.listo();
            desactualizada = false;
            if (error != null) {
                contenido.getChildren().setAll(Nodos.etiqueta("No se pudo analizar: " + error.getMessage(), "error"));
                return;
            }
            pintar(analisis);
            if (conEntrada) {
                entrar();
            }
        }));
    }

    private void entrar() {
        if (revelando != null) {
            revelando.stop();
        }
        revelando = Movimiento.revelar(List.copyOf(contenido.getChildren()));
    }

    private void pintar(Analisis a) {
        var secciones = new ArrayList<Node>(List.of(encabezado(a), hero(a), ritmo(a), flujo(a)));
        if (!a.porCategoria().isEmpty()) {
            secciones.add(porCategoria(a));
        }
        if (!a.frecuentes().isEmpty()) {
            secciones.add(frecuentes(a));
        }
        if (a.hormiga().veces() > 0) {
            secciones.add(hormiga(a));
        }
        if (!a.suscripciones().lista().isEmpty()) {
            secciones.add(recurrentes(a));
        }
        contenido.getChildren().setAll(secciones);
    }

    // ---- Encabezado y hero ----

    private Node encabezado(Analisis a) {
        var fila = new HBox(10, Nodos.etiqueta("Analíticas", "titulo"), Nodos.etiqueta("90 días", "titulo", "titulo-tenue"),
                Nodos.espaciador(), Nodos.etiqueta(Formato.fechaCorta(a.desde(), a.hasta()) + " – hoy  ·  sin deudas",
                        "seccion-detalle"));
        fila.setAlignment(Pos.BASELINE_LEFT);
        fila.getStyleClass().add("encabezado");
        return fila;
    }

    /** Izquierda: cuánto podrías ahorrar al mes (como el saldo del Resumen). Derecha: las ideas. */
    private Node hero(Analisis a) {
        if (a.pocosDatos()) {
            var texto = Nodos.etiqueta("Con " + a.gastosAnalizados() + (a.gastosAnalizados() == 1 ? " gasto" : " gastos")
                    + " en los últimos 90 días todavía no hay hábitos que leer. Vuelve cuando tengas al menos "
                    + Analisis.MINIMO_GASTOS + ".", "subtitulo");
            texto.setWrapText(true);
            var hero = new VBox(texto);
            hero.getStyleClass().add("hero");
            return hero;
        }

        BigDecimal ahorro = a.ahorroPosible();
        String[] partes = Formato.montoPartido(ahorro);
        var cifra = new HBox(Nodos.etiqueta(partes[0], "saldo-entero"), Nodos.etiqueta(partes[1], "saldo-decimales"));
        cifra.setAlignment(Pos.BASELINE_LEFT);
        var izquierda = new VBox(10, Nodos.etiqueta("Podrías ahorrar al mes", "etiqueta"), cifra,
                Nodos.etiqueta("Gastas ≈ " + Formato.monto(a.gastoMensual()) + " al mes", "etiqueta"));
        a.impacto().ifPresent(i -> {
            var impacto = Nodos.etiqueta(textoImpacto(i), "analitica-impacto");
            impacto.setWrapText(true);
            impacto.setMinHeight(Region.USE_PREF_SIZE); // que el VBox no lo corte a una línea
            izquierda.getChildren().add(impacto);
        });
        // Ancho fijo: la frase de la meta se ajusta en varias líneas y las ideas ocupan el resto
        izquierda.setMinWidth(300);
        izquierda.setPrefWidth(320);
        izquierda.setMaxWidth(320);

        var ideas = new VBox(4);
        ideas.getChildren().add(Nodos.etiqueta("Ideas", "seccion-titulo"));
        if (a.ideas().isEmpty()) {
            ideas.getChildren().add(Nodos.etiqueta("Nada que recortar por ahora: no hay hábitos repetidos ni subidas.",
                    "seccion-detalle"));
        }
        for (Analisis.IdeaAhorro idea : a.ideas().stream().limit(5).toList()) {
            ideas.getChildren().add(filaIdea(idea));
        }
        HBox.setHgrow(ideas, Priority.ALWAYS);

        var hero = new HBox(48, izquierda, ideas);
        hero.setAlignment(Pos.TOP_LEFT);
        hero.getStyleClass().add("hero");
        return hero;
    }

    private static String textoImpacto(Analisis.ImpactoMeta i) {
        String meta = "«" + i.meta() + "»";
        if (i.mesesAlRitmoActual() == null) {
            return "Apartándolo para " + meta + ", la completarías en " + meses(i.mesesConAhorro()) + ".";
        }
        if (i.mesesAlRitmoActual() <= i.mesesConAhorro()) {
            return "A tu ritmo, " + meta + " se completa en " + meses(i.mesesAlRitmoActual()) + ".";
        }
        return "Si lo apartas para " + meta + ", llegarías en " + meses(i.mesesConAhorro()) + " en vez de "
                + meses(i.mesesAlRitmoActual()) + ".";
    }

    private static String meses(int n) {
        return n == 1 ? "1 mes" : n + " meses";
    }

    private static Node filaIdea(Analisis.IdeaAhorro idea) {
        String texto = switch (idea.tipo()) {
            case REDUCIR_FRECUENTE -> "Reducir «" + idea.sobre() + "» a la mitad";
            case REDUCIR_HORMIGA -> "Recortar a la mitad las compras chicas";
            case VOLVER_AL_PROMEDIO -> "Volver a tu promedio en " + idea.sobre();
            case REVISAR_RECURRENTE -> "Revisar «" + idea.sobre() + "», tu recurrente más caro";
        };
        var nombre = Nodos.etiqueta(texto, "leyenda-nombre");
        var columna = new VBox(2, nombre);
        if (!idea.suma()) {
            // Se cruza con otras ideas (p. ej. los cafés también son "Comida"): no entra en el total
            columna.getChildren().add(Nodos.etiqueta("Aparte del total", "leyenda-detalle"));
        }
        HBox.setHgrow(columna, Priority.ALWAYS);
        var monto = Nodos.etiqueta("≈ " + Formato.monto(idea.porMes()) + " al mes", "leyenda-monto");
        monto.setMinWidth(Region.USE_PREF_SIZE); // la cifra nunca se corta; si falta espacio, cede el texto
        var fila = new HBox(12, columna, monto);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().add("analitica-idea");
        return fila;
    }

    // ---- Secciones ----

    private Node frecuentes(Analisis a) {
        BigDecimal mayor = a.frecuentes().stream().map(Analisis.GastoFrecuente::porMes).max(BigDecimal::compareTo)
                .orElse(BigDecimal.ONE);
        var filas = new ArrayList<Node>();
        int puesto = 1;
        for (Analisis.GastoFrecuente f : a.frecuentes()) {
            var cabecera = new HBox(12,
                    Nodos.etiqueta(String.valueOf(puesto++), "analitica-puesto"),
                    Nodos.etiqueta(f.nombre(), "leyenda-nombre"),
                    Nodos.etiqueta(f.veces() + " veces", "leyenda-detalle"),
                    Nodos.espaciador(),
                    Nodos.etiqueta("≈ " + Formato.monto(f.porMes()) + " al mes", "leyenda-monto"));
            cabecera.setAlignment(Pos.CENTER_LEFT);
            double proporcion = f.porMes().divide(mayor, 4, java.math.RoundingMode.HALF_UP).doubleValue();
            filas.add(new VBox(10, cabecera, VistaListaDetalle.barraProgreso(proporcion, COLOR_BARRA, false)));
        }
        var lista = new VBox(18);
        lista.getChildren().addAll(filas);
        return new VBox(16, encabezado("Lo que más repites", "Sin recurrentes ni deudas"), lista);
    }

    private Node hormiga(Analisis a) {
        Analisis.Hormiga h = a.hormiga();
        String[] partes = Formato.montoPartido(h.porMes());
        var cifra = new HBox(Nodos.etiqueta(partes[0], "cifra-media"), Nodos.etiqueta(partes[1], "cifra-media-decimales"),
                Nodos.etiqueta("  al mes", "subtitulo"));
        cifra.setAlignment(Pos.BASELINE_LEFT);
        var explicacion = Nodos.etiqueta(h.veces() + (h.veces() == 1 ? " compra" : " compras") + " de Q"
                + h.umbral().stripTrailingZeros().toPlainString() + " o menos en 90 días: el "
                + Formato.porcentaje(h.proporcion()) + " de lo que gastas.", "subtitulo");
        explicacion.setWrapText(true);
        var ejemplos = new FlowPane(8, 8);
        h.ejemplos().forEach(e -> ejemplos.getChildren().add(VistaListaDetalle.dato(e)));
        var tarjeta = new VBox(12, cifra, explicacion, ejemplos);
        tarjeta.getStyleClass().add("analitica-tarjeta");
        return new VBox(16, encabezado("Gastos hormiga", "Chicos, frecuentes y que juntos pesan"), tarjeta);
    }

    // ---- Comparativos ----

    /** Gasto acumulado del mes contra el mes pasado: ¿vas más rápido o más lento? */
    private Node ritmo(Analisis a) {
        Analisis.Ritmo r = a.ritmo();
        YearMonth anterior = r.mes().minusMonths(1);
        int dias = Math.max(r.mes().lengthOfMonth(), anterior.lengthOfMonth());
        List<String> eje = IntStream.rangeClosed(1, dias).mapToObj(String::valueOf).toList();
        var grafica = new GraficaCurva(eje, i -> i == 0 || (i + 1) % 5 == 0, i -> "Día " + (i + 1), List.of(
                new GraficaCurva.Serie(Formato.capitalizar(Formato.nombreMes(r.mes())), r.actual(), COLOR_PRINCIPAL,
                        GraficaCurva.Trazo.CONTINUO),
                new GraficaCurva.Serie(Formato.capitalizar(Formato.nombreMes(anterior)), r.anterior(), COLOR_COMPARACION,
                        GraficaCurva.Trazo.DISCONTINUO)));

        var lectura = new HBox(10);
        lectura.setAlignment(Pos.CENTER_LEFT);
        BigDecimal antes = r.anteriorAlDia();
        if (antes.signum() > 0) {
            BigDecimal cambio = r.actualAlDia().subtract(antes).multiply(BigDecimal.valueOf(100))
                    .divide(antes, 1, RoundingMode.HALF_UP);
            lectura.getChildren().addAll(chipVariacion(cambio), Nodos.etiqueta((cambio.signum() > 0 ? "más" : "menos")
                    + " que " + Formato.nombreMes(anterior) + " a estas alturas  ·  "
                    + Formato.monto(r.actualAlDia()) + " vs " + Formato.monto(antes), "etiqueta"));
        }
        return new VBox(16, encabezado("Ritmo del mes", "Gasto acumulado día a día"), lectura,
                grafica.leyenda(), grafica);
    }

    /** Ingresos contra gastos de los últimos meses, en el mismo eje (los dos son quetzales). */
    private Node flujo(Analisis a) {
        List<Analisis.PuntoMes> meses = a.flujo();
        List<String> eje = meses.stream()
                .map(p -> p.mes().getMonth().getDisplayName(TextStyle.SHORT, ES).replace(".", ""))
                .toList();
        List<BigDecimal> gastos = meses.stream().map(Analisis.PuntoMes::gastos).toList();
        List<BigDecimal> ingresos = meses.stream().map(Analisis.PuntoMes::ingresos).toList();
        boolean hayIngresos = ingresos.stream().anyMatch(v -> v.signum() > 0);

        var series = new ArrayList<GraficaCurva.Serie>();
        series.add(new GraficaCurva.Serie("Gastos", gastos, COLOR_PRINCIPAL, GraficaCurva.Trazo.CONTINUO));
        if (hayIngresos) {
            series.add(new GraficaCurva.Serie("Ingresos", ingresos, COLOR_COMPARACION, GraficaCurva.Trazo.DISCONTINUO));
        }
        var grafica = new GraficaCurva(eje, i -> true,
                i -> Formato.capitalizar(Formato.nombreMes(meses.get(i).mes())) + " " + meses.get(i).mes().getYear(),
                series);

        String detalle;
        if (!hayIngresos) {
            detalle = "Registra tus ingresos en Cuentas para compararlos";
        } else {
            Analisis.PuntoMes ultimo = meses.getLast();
            BigDecimal queda = ultimo.ingresos().subtract(ultimo.gastos());
            detalle = (queda.signum() >= 0 ? "Este mes te quedan " : "Este mes gastaste de más ")
                    + Formato.monto(queda.abs()) + "  ·  gastos con abonos a deudas";
        }
        return new VBox(16, encabezado("Ingresos y gastos", detalle), grafica.leyenda(), grafica);
    }

    /**
     * Cada categoría: una barra con lo de los últimos 30 días y una marca vertical en su
     * promedio mensual (gráfica "bullet"). La barra que pasa la marca es la que subió.
     */
    private Node porCategoria(Analisis a) {
        BigDecimal mayor = a.porCategoria().stream()
                .map(c -> c.ultimos30().max(c.promedio()))
                .max(BigDecimal::compareTo).filter(v -> v.signum() > 0).orElse(BigDecimal.ONE);
        var lista = new VBox(18);
        for (Analisis.ComparativoCategoria c : a.porCategoria().stream().limit(6).toList()) {
            Optional<Analisis.Tendencia> subio = a.tendencias().stream()
                    .filter(t -> t.categoria() == c.categoria()).findFirst();
            var nombre = new HBox(10, Nodos.punto(Paleta.colorDe(c.categoria())),
                    Nodos.etiqueta(c.categoria() == null ? "Sin categoría" : c.categoria().nombre(), "leyenda-nombre"));
            nombre.setAlignment(Pos.CENTER_LEFT);
            subio.ifPresent(t -> nombre.getChildren().add(chipVariacion(t.variacion())));
            var cabecera = new HBox(12, nombre, Nodos.espaciador(),
                    Nodos.etiqueta(Formato.monto(c.ultimos30()), "leyenda-monto"),
                    Nodos.etiqueta(c.promedio().signum() == 0 ? "sin historia"
                            : "promedio " + Formato.monto(c.promedio()), "leyenda-detalle"));
            cabecera.setAlignment(Pos.BASELINE_LEFT);
            lista.getChildren().add(new VBox(10, cabecera, barraConMarca(proporcion(c.ultimos30(), mayor),
                    c.promedio().signum() == 0 ? -1 : proporcion(c.promedio(), mayor), Paleta.colorDe(c.categoria()))));
        }
        var leyenda = new HBox(20, muestraLeyenda("grafica-bala-muestra", "Últimos 30 días"),
                muestraLeyenda("grafica-bala-marca-muestra", "Tu promedio mensual"));
        return new VBox(16, encabezado("Por categoría", "Últimos 30 días frente a los 90 anteriores"), leyenda, lista);
    }

    /** Barra de lo reciente y una marca fina en el promedio (marca < 0 = sin historia, no se dibuja). */
    private static Node barraConMarca(double valor, double marca, String color) {
        var pista = new Pane();
        pista.getStyleClass().add("grafica-bala");
        pista.setPrefHeight(10);
        pista.setMinHeight(10);
        var barra = new Region();
        barra.getStyleClass().add("grafica-bala-barra");
        barra.setStyle("-fx-background-color: " + color + ";");
        barra.prefWidthProperty().bind(pista.widthProperty().multiply(valor));
        barra.setPrefHeight(10);
        pista.getChildren().add(barra);
        if (marca >= 0) {
            var linea = new Region();
            linea.getStyleClass().add("grafica-bala-marca");
            linea.layoutXProperty().bind(pista.widthProperty().multiply(marca).subtract(1));
            linea.setLayoutY(-4);
            linea.setPrefSize(2, 18);
            pista.getChildren().add(linea);
        }
        return pista;
    }

    private static HBox muestraLeyenda(String clase, String texto) {
        var muestra = new Region();
        muestra.getStyleClass().add(clase);
        var item = new HBox(8, muestra, Nodos.etiqueta(texto, "grafica-leyenda"));
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }

    /** El chip de variación del Resumen: triángulo arriba o abajo y el porcentaje, sin color semántico. */
    private static Label chipVariacion(BigDecimal porcentaje) {
        boolean sube = porcentaje.signum() > 0;
        var chip = new Label(porcentaje.abs().stripTrailingZeros().toPlainString() + "%",
                (sube ? Iconos.SUBE : Iconos.BAJA).crear("icono-chico"));
        chip.getStyleClass().addAll("variacion", sube ? "variacion-sube" : "variacion-baja");
        return chip;
    }

    private static double proporcion(BigDecimal parte, BigDecimal total) {
        return Math.min(1, parte.divide(total, 4, RoundingMode.HALF_UP).doubleValue());
    }

    private Node recurrentes(Analisis a) {
        Analisis.Suscripciones s = a.suscripciones();
        var lista = new VBox(2);
        for (Analisis.CostoRecurrente c : s.lista()) {
            var fila = new HBox(12,
                    Nodos.etiqueta(c.recurrencia().descripcion(), "leyenda-nombre"),
                    Nodos.etiqueta(Formato.frecuencia(c.recurrencia()), "leyenda-detalle"),
                    Nodos.espaciador(),
                    Nodos.etiqueta("≈ " + Formato.monto(c.porMes()) + " al mes", "leyenda-detalle"),
                    Nodos.etiqueta(Formato.monto(c.porAnio()) + " al año", "celda-monto"));
            fila.setAlignment(Pos.CENTER_LEFT);
            fila.getStyleClass().add("tabla-fila");
            lista.getChildren().add(fila);
        }
        lista.getStyleClass().add("tabla");
        String detalle = "≈ " + Formato.monto(s.porAnio()) + " al año"
                + (s.proporcion() > 0 ? "  ·  " + Formato.porcentaje(s.proporcion()) + " de tu gasto mensual" : "");
        return new VBox(16, encabezado("Recurrentes", detalle), lista);
    }

    private static Node encabezado(String titulo, String detalle) {
        var fila = new HBox(Nodos.etiqueta(titulo, "seccion-titulo"), Nodos.espaciador(),
                Nodos.etiqueta(detalle, "seccion-detalle"));
        fila.setAlignment(Pos.BASELINE_LEFT);
        return fila;
    }
}
