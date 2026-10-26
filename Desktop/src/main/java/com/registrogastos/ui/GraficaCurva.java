package com.registrogastos.ui;

import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/**
 * Gráfica de líneas curvas para comparar series en un mismo eje (siempre uno solo: dos
 * escalas en una gráfica engañan). La serie principal va continua y con un área tenue; las de
 * comparación, discontinuas. Así se distinguen aunque no se vea el color. Cada línea termina
 * con su valor escrito a la derecha y, al pasar el mouse, una guía vertical muestra los
 * valores de ese punto.
 *
 * Las curvas son MONÓTONAS (interpolación de Fritsch–Carlson, la misma de d3.curveMonotoneX):
 * entre dos puntos la curva nunca sube más que el mayor ni baja más que el menor. Una curva
 * suave cualquiera "rebota" y, con dinero, dibujaría valores que no existieron (por ejemplo,
 * bajo cero).
 */
final class GraficaCurva extends Region {

    enum Trazo { CONTINUO, DISCONTINUO }

    /**
     * @param valores puede tener menos puntos que el eje: el mes en curso termina en hoy
     * @param color   el color identifica la serie; los textos siempre van en tonos de texto
     */
    record Serie(String nombre, List<BigDecimal> valores, String color, Trazo trazo) {
    }

    // Márgenes del área de dibujo: a la izquierda el eje, a la derecha las etiquetas directas
    private static final double IZQ = 56;
    private static final double DER = 104;
    private static final double ARRIBA = 12;
    private static final double ABAJO = 28;
    private static final int MARCAS = 3;

    private final List<String> eje;
    private final IntPredicate enEje;
    private final IntFunction<String> titulo;
    private final List<Serie> series;
    private final double maximo;

    private final Pane fondo = new Pane();
    private final Pane trazos = new Pane();
    private final Rectangle recorte = new Rectangle();
    private final Line guia = new Line();
    private final List<Circle> marcadores = new ArrayList<>();
    private final VBox globo = new VBox(6);
    private double avance;
    private double anchoPintado = -1;
    private double altoPintado = -1;

    /**
     * @param eje    una etiqueta por punto del eje X
     * @param enEje  cuáles de esas etiquetas se escriben bajo el eje (no caben todas)
     * @param titulo el encabezado del globo para cada punto ("Día 12", "Agosto 2026")
     */
    GraficaCurva(List<String> eje, IntPredicate enEje, IntFunction<String> titulo, List<Serie> series) {
        this.eje = List.copyOf(eje);
        this.enEje = enEje;
        this.titulo = titulo;
        this.series = List.copyOf(series);
        this.maximo = techo(series.stream().flatMap(s -> s.valores().stream())
                .max(BigDecimal::compareTo).orElse(BigDecimal.ONE).doubleValue());
        getStyleClass().add("grafica-curva");
        setMinHeight(200);
        setPrefHeight(230);

        trazos.setClip(recorte);
        guia.getStyleClass().add("grafica-guia");
        guia.setVisible(false);
        guia.setMouseTransparent(true);
        for (Serie s : series) {
            var marcador = new Circle(4.5, Color.web(s.color()));
            marcador.getStyleClass().add("grafica-marcador");
            marcador.setVisible(false);
            marcador.setMouseTransparent(true);
            marcadores.add(marcador);
        }
        globo.getStyleClass().add("grafica-globo");
        globo.setManaged(false);
        globo.setVisible(false);
        globo.setMouseTransparent(true);
        getChildren().addAll(fondo, trazos, guia);
        getChildren().addAll(marcadores);
        getChildren().add(globo);

        setOnMouseMoved(e -> senalar(e.getX()));
        setOnMouseExited(e -> ocultarSenal());
        // Las líneas se dibujan de izquierda a derecha al aparecer (se apaga con "reducir movimiento")
        Movimiento.progreso(Duration.millis(900), f -> {
            avance = f;
            recorte.setWidth(getWidth() * f);
        });
    }

    /** Leyenda: una muestra del trazo (continuo o discontinuo) y el nombre de cada serie. */
    HBox leyenda() {
        var fila = new HBox(20);
        for (Serie s : series) {
            var muestra = new Line(0, 0, 18, 0);
            muestra.setStroke(Color.web(s.color()));
            muestra.setStrokeWidth(2);
            muestra.setStrokeLineCap(StrokeLineCap.ROUND);
            if (s.trazo() == Trazo.DISCONTINUO) {
                muestra.getStrokeDashArray().setAll(5.0, 4.0);
            }
            var item = new HBox(8, muestra, Nodos.etiqueta(s.nombre(), "grafica-leyenda"));
            item.setAlignment(Pos.CENTER_LEFT);
            fila.getChildren().add(item);
        }
        return fila;
    }

    // ---- Dibujo ----

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        fondo.resizeRelocate(0, 0, w, h);
        trazos.resizeRelocate(0, 0, w, h);
        recorte.setHeight(h);
        recorte.setWidth(w * avance);
        if (w != anchoPintado || h != altoPintado) {
            anchoPintado = w;
            altoPintado = h;
            pintar(w, h);
        }
    }

    private void pintar(double w, double h) {
        fondo.getChildren().clear();
        trazos.getChildren().clear();
        if (w <= IZQ + DER || eje.size() < 2) {
            return;
        }
        // Rejilla y eje Y: 4 líneas tenues (0, 1/3, 2/3 y el techo) con su monto a la izquierda
        for (int k = 0; k <= MARCAS; k++) {
            double valor = maximo * k / MARCAS;
            double y = yDe(valor, h);
            var linea = new Line(IZQ, y, w - DER, y);
            linea.getStyleClass().add(k == 0 ? "grafica-base" : "grafica-rejilla");
            fondo.getChildren().add(linea);
            var texto = etiqueta(fondo, entero(valor), "grafica-eje");
            texto.setPrefWidth(IZQ - 12);
            texto.setAlignment(Pos.CENTER_RIGHT);
            texto.relocate(0, y - 8);
        }
        for (int i = 0; i < eje.size(); i++) {
            if (enEje.test(i)) {
                var texto = etiqueta(fondo, eje.get(i), "grafica-eje");
                texto.setPrefWidth(60);
                texto.setAlignment(Pos.CENTER);
                texto.relocate(xDe(i, w) - 30, h - ABAJO + 8);
            }
        }

        // De la última a la primera: la serie principal (la primera) queda encima
        var finales = new ArrayList<double[]>();
        for (int s = series.size() - 1; s >= 0; s--) {
            Serie serie = series.get(s);
            if (serie.valores().isEmpty()) {
                continue;
            }
            var puntos = new ArrayList<Point2D>();
            for (int i = 0; i < serie.valores().size(); i++) {
                puntos.add(new Point2D(xDe(i, w), yDe(serie.valores().get(i).doubleValue(), h)));
            }
            Color color = Color.web(serie.color());
            if (serie.trazo() == Trazo.CONTINUO) {
                trazos.getChildren().add(area(puntos, h - ABAJO, color));
            }
            var linea = curva(puntos);
            linea.setStroke(color);
            linea.setStrokeWidth(2);
            linea.setStrokeLineCap(StrokeLineCap.ROUND);
            linea.setFill(null);
            if (serie.trazo() == Trazo.DISCONTINUO) {
                linea.getStrokeDashArray().setAll(6.0, 5.0);
            }
            Point2D ultimo = puntos.getLast();
            var fin = new Circle(ultimo.getX(), ultimo.getY(), 3.5, color);
            fin.getStyleClass().add("grafica-marcador");
            trazos.getChildren().addAll(linea, fin);
            finales.add(new double[] {s, ultimo.getY()});
        }
        etiquetasDirectas(finales, w);
    }

    /**
     * El valor final de cada serie, a la derecha a la altura de su último punto. Si dos quedan
     * encimadas, se separan lo justo (34 px) para que se lean las dos.
     */
    private void etiquetasDirectas(List<double[]> finales, double w) {
        finales.sort((a, b) -> Double.compare(a[1], b[1]));
        double anterior = Double.NEGATIVE_INFINITY;
        for (double[] f : finales) {
            Serie serie = series.get((int) f[0]);
            double y = Math.max(f[1] - 16, anterior + 34);
            anterior = y;
            var nombre = etiqueta(fondo, serie.nombre(), "grafica-directa-nombre");
            var valor = etiqueta(fondo, Formato.monto(serie.valores().getLast()), "grafica-directa-valor");
            nombre.relocate(w - DER + 12, y);
            valor.relocate(w - DER + 12, y + 14);
        }
    }

    /** Curva monótona por los puntos (ver la explicación de la clase). */
    static Path curva(List<Point2D> p) {
        var path = new Path(new MoveTo(p.getFirst().getX(), p.getFirst().getY()));
        int n = p.size();
        if (n < 2) {
            return path;
        }
        double[] pendiente = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            pendiente[i] = (p.get(i + 1).getY() - p.get(i).getY()) / (p.get(i + 1).getX() - p.get(i).getX());
        }
        // Tangente en cada punto: 0 si la serie cambia de dirección (es un pico o un valle),
        // y nunca más empinada de lo que permiten los tramos vecinos
        double[] tangente = new double[n];
        tangente[0] = pendiente[0];
        tangente[n - 1] = pendiente[n - 2];
        for (int i = 1; i < n - 1; i++) {
            double s0 = pendiente[i - 1];
            double s1 = pendiente[i];
            double h0 = p.get(i).getX() - p.get(i - 1).getX();
            double h1 = p.get(i + 1).getX() - p.get(i).getX();
            double ponderada = (s0 * h1 + s1 * h0) / (h0 + h1);
            tangente[i] = (Math.signum(s0) + Math.signum(s1))
                    * Math.min(Math.min(Math.abs(s0), Math.abs(s1)), 0.5 * Math.abs(ponderada));
        }
        for (int i = 0; i < n - 1; i++) {
            Point2D a = p.get(i);
            Point2D b = p.get(i + 1);
            double tercio = (b.getX() - a.getX()) / 3;
            path.getElements().add(new CubicCurveTo(
                    a.getX() + tercio, a.getY() + tangente[i] * tercio,
                    b.getX() - tercio, b.getY() - tangente[i + 1] * tercio,
                    b.getX(), b.getY()));
        }
        return path;
    }

    /** La misma curva cerrada contra la base, rellena con el color desvaneciéndose hacia abajo. */
    private static Path area(List<Point2D> puntos, double base, Color color) {
        Path area = curva(puntos);
        area.getElements().addAll(new LineTo(puntos.getLast().getX(), base),
                new LineTo(puntos.getFirst().getX(), base), new ClosePath());
        area.setStroke(null);
        area.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, color.deriveColor(0, 1, 1, 0.20)), new Stop(1, color.deriveColor(0, 1, 1, 0))));
        return area;
    }

    // ---- Lectura con el mouse ----

    private void senalar(double x) {
        double w = getWidth();
        double h = getHeight();
        if (x < IZQ - 8 || x > w - DER + 8) {
            ocultarSenal();
            return;
        }
        int i = (int) Math.round((x - IZQ) / paso(w));
        i = Math.clamp(i, 0, eje.size() - 1);
        double xi = xDe(i, w);
        guia.setStartX(xi);
        guia.setEndX(xi);
        guia.setStartY(ARRIBA);
        guia.setEndY(h - ABAJO);
        guia.setVisible(true);

        globo.getChildren().setAll(Nodos.etiqueta(titulo.apply(i), "grafica-globo-titulo"));
        for (int s = 0; s < series.size(); s++) {
            Serie serie = series.get(s);
            Circle marcador = marcadores.get(s);
            boolean tiene = i < serie.valores().size();
            marcador.setVisible(tiene);
            if (!tiene) {
                continue;
            }
            BigDecimal valor = serie.valores().get(i);
            marcador.setCenterX(xi);
            marcador.setCenterY(yDe(valor.doubleValue(), h));
            var muestra = new Region();
            muestra.getStyleClass().add("grafica-globo-muestra");
            muestra.setStyle("-fx-background-color: " + serie.color() + ";");
            var fila = new HBox(8, muestra, Nodos.etiqueta(serie.nombre(), "grafica-globo-nombre"), Nodos.espaciador(),
                    Nodos.etiqueta(Formato.monto(valor), "grafica-globo-valor"));
            fila.setAlignment(Pos.CENTER_LEFT);
            globo.getChildren().add(fila);
        }
        globo.setVisible(true);
        globo.applyCss();
        globo.autosize();
        // A la derecha de la guía; si no cabe, a la izquierda
        double gx = xi + 14 + globo.getWidth() > w ? xi - 14 - globo.getWidth() : xi + 14;
        globo.relocate(gx, ARRIBA);
    }

    private void ocultarSenal() {
        guia.setVisible(false);
        globo.setVisible(false);
        marcadores.forEach(m -> m.setVisible(false));
    }

    // ---- Escalas ----

    private double paso(double w) {
        return (w - IZQ - DER) / (eje.size() - 1);
    }

    private double xDe(int i, double w) {
        return IZQ + i * paso(w);
    }

    private double yDe(double valor, double h) {
        double alto = h - ARRIBA - ABAJO;
        return ARRIBA + alto * (1 - valor / maximo);
    }

    /**
     * Techo "redondo" del eje: el menor múltiplo de un paso limpio (1, 2, 2.5 o 5 × 10ⁿ) que
     * deja al máximo por debajo. Así las 3 marcas caen en números legibles (0, 500, 1,000, 1,500).
     */
    static double techo(double maximo) {
        if (maximo <= 0) {
            return MARCAS;
        }
        double bruto = maximo / MARCAS;
        double potencia = Math.pow(10, Math.floor(Math.log10(bruto)));
        for (double factor : new double[] {1, 2, 2.5, 5, 10}) {
            if (factor * potencia >= bruto) {
                return factor * potencia * MARCAS;
            }
        }
        return 10 * potencia * MARCAS;
    }

    private static String entero(double valor) {
        return new DecimalFormat("#,##0").format(BigDecimal.valueOf(valor).setScale(0, RoundingMode.HALF_UP));
    }

    /** Agrega la etiqueta y le aplica el CSS ya, para que su primer cuadro no salga sin estilo. */
    private static Label etiqueta(Pane donde, String texto, String clase) {
        var etiqueta = Nodos.etiqueta(texto, clase);
        donde.getChildren().add(etiqueta);
        etiqueta.applyCss();
        return etiqueta;
    }
}
