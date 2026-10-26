package com.registrogastos.servicio;

import com.registrogastos.dominio.Categoria;
import com.registrogastos.dominio.Recurrencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Resultado de {@link AnaliticaServicio}: una foto inmutable, ya calculada, que la vista solo
 * pinta. Todos los montos están en moneda principal y los "por mes" son promedios del periodo.
 * Los abonos a deudas no entran: pagar una deuda no es un hábito de consumo.
 *
 * @param gastoMensual promedio mensual de todo lo analizado
 */
public record Analisis(
        LocalDate desde,
        LocalDate hasta,
        int gastosAnalizados,
        BigDecimal gastoMensual,
        List<GastoFrecuente> frecuentes,
        Hormiga hormiga,
        List<Tendencia> tendencias,
        Suscripciones suscripciones,
        List<IdeaAhorro> ideas,
        Optional<ImpactoMeta> impacto,
        Ritmo ritmo,
        List<PuntoMes> flujo,
        List<ComparativoCategoria> porCategoria) {

    public Analisis {
        frecuentes = List.copyOf(frecuentes);
        tendencias = List.copyOf(tendencias);
        ideas = List.copyOf(ideas);
        flujo = List.copyOf(flujo);
        porCategoria = List.copyOf(porCategoria);
    }

    /** Con menos de esto, los promedios dirían poco: la vista lo avisa. */
    public static final int MINIMO_GASTOS = 8;

    public boolean pocosDatos() {
        return gastosAnalizados < MINIMO_GASTOS;
    }

    /**
     * Suma de las ideas que no se pisan entre sí (ver {@link IdeaAhorro#suma()}): lo que de
     * verdad se ahorraría al aplicarlas todas.
     */
    public BigDecimal ahorroPosible() {
        return ideas.stream().filter(IdeaAhorro::suma).map(IdeaAhorro::porMes).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Gasto acumulado día a día: este mes (hasta hoy) contra el mes anterior completo. Incluye
     * los abonos a deudas, igual que el Resumen: el último punto es el total del mes.
     *
     * @param actual   índice 0 = día 1; tantos puntos como días van del mes
     * @param anterior todos los días del mes anterior
     */
    public record Ritmo(YearMonth mes, List<BigDecimal> actual, List<BigDecimal> anterior) {

        public Ritmo {
            actual = List.copyOf(actual);
            anterior = List.copyOf(anterior);
        }

        /** Lo que llevabas el mes pasado a esta misma altura del mes. */
        public BigDecimal anteriorAlDia() {
            if (anterior.isEmpty() || actual.isEmpty()) {
                return BigDecimal.ZERO;
            }
            return anterior.get(Math.min(actual.size(), anterior.size()) - 1);
        }

        public BigDecimal actualAlDia() {
            return actual.isEmpty() ? BigDecimal.ZERO : actual.getLast();
        }
    }

    /** Un mes del comparativo de 6 meses (el último va en curso). Gastos incluye abonos a deudas. */
    public record PuntoMes(YearMonth mes, BigDecimal gastos, BigDecimal ingresos) {
    }

    /**
     * Una categoría: últimos 30 días frente a su promedio mensual de los 90 anteriores.
     *
     * @param categoria null = sin categoría
     */
    public record ComparativoCategoria(Categoria categoria, BigDecimal ultimos30, BigDecimal promedio) {
    }

    /** Algo que compras seguido (misma descripción). */
    public record GastoFrecuente(String nombre, int veces, BigDecimal total, BigDecimal porMes) {
    }

    /**
     * Compras chicas y frecuentes que juntas pesan.
     *
     * @param proporcion de 0 a 1 respecto a todo lo gastado en el periodo
     * @param ejemplos   las descripciones que más se repiten entre ellas
     */
    public record Hormiga(BigDecimal umbral, int veces, BigDecimal porMes, double proporcion, List<String> ejemplos) {

        public Hormiga {
            ejemplos = List.copyOf(ejemplos);
        }
    }

    /**
     * Una categoría que subió frente a su costumbre.
     *
     * @param categoria null = sin categoría
     * @param variacion en porcentaje (45.0 = subió 45 %)
     */
    public record Tendencia(Categoria categoria, BigDecimal ultimos30, BigDecimal promedio, BigDecimal variacion) {

        public BigDecimal exceso() {
            return ultimos30.subtract(promedio);
        }
    }

    public record CostoRecurrente(Recurrencia recurrencia, BigDecimal porMes, BigDecimal porAnio) {
    }

    /** @param proporcion de 0 a 1: cuánto del gasto mensual se va en recurrentes */
    public record Suscripciones(List<CostoRecurrente> lista, BigDecimal porMes, BigDecimal porAnio, double proporcion) {

        public Suscripciones {
            lista = List.copyOf(lista);
        }
    }

    /**
     * Una forma concreta de ahorrar.
     *
     * @param sobre qué cosa (la descripción, la categoría o la recurrencia)
     * @param suma  true si no se pisa con las demás que suman. Una tendencia de "Comida" puede
     *              incluir los mismos cafés de otra idea; contarla también inflaría el total.
     */
    public record IdeaAhorro(Tipo tipo, String sobre, BigDecimal porMes, boolean suma) {

        public enum Tipo { REDUCIR_FRECUENTE, REDUCIR_HORMIGA, VOLVER_AL_PROMEDIO, REVISAR_RECURRENTE }
    }

    /**
     * Qué pasaría con una meta si lo ahorrado se aportara.
     *
     * @param mesesAlRitmoActual null si en el periodo no se le aportó nada
     */
    public record ImpactoMeta(String meta, BigDecimal restante, Integer mesesAlRitmoActual, int mesesConAhorro,
                              BigDecimal ahorroMensual) {
    }
}
