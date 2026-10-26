package com.registrogastos.servicio;

import com.registrogastos.dominio.Abono;
import com.registrogastos.dominio.Categoria;
import com.registrogastos.dominio.Gasto;
import com.registrogastos.dominio.Meta;
import com.registrogastos.dominio.Moneda;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El análisis es una función pura: se le pasan listas armadas a mano, sin base de datos. */
class AnaliticaTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 25);
    private static final Instant AHORA = Instant.parse("2026-10-25T12:00:00Z");
    private static final Categoria COMIDA = Categoria.nueva("Comida", Categoria.PALETA.getFirst(), AHORA);

    private final List<Gasto> gastos = new ArrayList<>();

    @Test
    void agrupaLoQueRepitesSinImportarTildesNiMayusculas() {
        gasto("Café", "15", 1);
        gasto("cafe", "15", 2);
        gasto(" CAFÉ ", "15", 3);
        gasto("Cine", "60", 4); // una sola vez: no es hábito
        rellenar();

        var a = analizar();
        var cafe = a.frecuentes().getFirst();
        assertEquals(3, cafe.veces());
        assertEquals(new BigDecimal("45.00"), cafe.total());
        assertTrue(a.frecuentes().stream().noneMatch(f -> f.nombre().equals("Cine")));
    }

    @Test
    void hormigaCuentaSoloComprasChicasYNiRecurrentesNiDeudas() {
        gasto("Chicle", "5", 1);
        gasto("Agua", "50", 2);                 // justo en el umbral: cuenta
        gasto("Zapatos", "300", 3);             // grande: no cuenta
        gastos.add(new Gasto(UUID.randomUUID().toString(), "Netflix", new BigDecimal("40"), HOY, null,
                "rec-1", null, null, AHORA, AHORA, false));  // recurrente: no cuenta
        gastos.add(new Gasto(UUID.randomUUID().toString(), "Abono", new BigDecimal("30"), HOY, null,
                null, "deuda-1", null, AHORA, AHORA, false)); // abono a deuda: ni siquiera se analiza
        rellenar();

        var h = analizar().hormiga();
        assertEquals(2 + 8, h.veces()); // chicle y agua + los 8 de relleno (de 10 cada uno)
        assertFalse(h.ejemplos().contains("Netflix"));
    }

    @Test
    void detectaUnaCategoriaQueSubioFrenteASuCostumbre() {
        // Antes: 100 al mes en Comida (300 en los 90 días previos). Ahora: 200 en los últimos 30
        for (int i = 0; i < 3; i++) {
            gasto("Súper", "100", 40 + i * 30, COMIDA);
        }
        gasto("Súper", "200", 5, COMIDA);
        rellenar();

        var tendencia = analizar().tendencias().getFirst();
        assertEquals(COMIDA, tendencia.categoria());
        assertEquals(new BigDecimal("100.0"), tendencia.variacion());
    }

    @Test
    void elAhorroNoCuentaDosVecesLoMismo() {
        // 6 cafés de 20 = 120 en 90 días -> 40 al mes; la idea es la mitad: 20
        for (int i = 0; i < 6; i++) {
            gasto("Café", "20", i * 10 + 1);
        }
        // 4 chicles de 5 = 20 -> 6.67 al mes -> la mitad: 3.34 (redondeo hacia arriba en .335)
        for (int i = 0; i < 4; i++) {
            gasto("Chicle", "5", i * 10 + 2);
        }
        var a = analizar();
        assertEquals(new BigDecimal("20.00"), idea(a, Analisis.IdeaAhorro.Tipo.REDUCIR_FRECUENTE, "Café").porMes());
        // Cafés y chicles son frecuentes Y chicos: ya tienen su idea, así que la hormiga queda vacía
        // (si también se sumara, el mismo dinero se contaría dos veces)
        assertEquals(new BigDecimal("3.34"), idea(a, Analisis.IdeaAhorro.Tipo.REDUCIR_FRECUENTE, "Chicle").porMes());
        assertTrue(a.ideas().stream().noneMatch(i -> i.tipo() == Analisis.IdeaAhorro.Tipo.REDUCIR_HORMIGA));
        assertEquals(new BigDecimal("23.34"), a.ahorroPosible());
    }

    @Test
    void calculaCuantoAntesLlegariasAUnaMeta() {
        for (int i = 0; i < 9; i++) {
            gasto("Almuerzo", "60", i * 9 + 1); // 540 en 90 días -> 180 al mes -> idea: 90
        }
        var laptop = Meta.nueva("Laptop", new BigDecimal("1000"), null, Categoria.PALETA.getFirst(), AHORA);
        var estado = new EstadoMeta(laptop, new BigDecimal("100"), HOY);
        // Aportó 90 en el periodo -> ritmo 30 al mes -> 900 / 30 = 30 meses; con +90 al mes: 900 / 120 = 8
        var aportes = Map.of(laptop.id(), List.of(Abono.nuevo(laptop.id(), new BigDecimal("90"), HOY.minusDays(10), AHORA)));

        var impacto = AnaliticaServicio.analizar(gastos, List.of(), List.of(), List.of(estado), aportes,
                Conversor.SOLO_PRINCIPAL, HOY).impacto().orElseThrow();
        assertEquals(30, impacto.mesesAlRitmoActual());
        assertEquals(8, impacto.mesesConAhorro());
    }

    @Test
    void convierteLosDolaresAntesDeAnalizar() {
        var conv = new Conversor(Map.of("dolares", Moneda.USD), Map.of(Moneda.USD, new BigDecimal("7.70")));
        for (int i = 0; i < 8; i++) {
            gastos.add(new Gasto(UUID.randomUUID().toString(), "Steam", new BigDecimal("10"), HOY.minusDays(i), null,
                    null, null, "dolares", AHORA, AHORA, false));
        }
        var a = AnaliticaServicio.analizar(gastos, List.of(), List.of(), List.of(), Map.of(), conv, HOY);
        assertEquals(new BigDecimal("616.00"), a.frecuentes().getFirst().total()); // 8 × 10 × 7.70
        assertEquals(0, a.hormiga().veces()); // 77 quetzales cada una: ya no es "chica"
    }

    // ---- Ayudas ----

    private Analisis analizar() {
        return AnaliticaServicio.analizar(gastos, List.of(COMIDA), List.of(), List.of(), Map.of(),
                Conversor.SOLO_PRINCIPAL, HOY);
    }

    private void gasto(String descripcion, String monto, int haceDias) {
        gasto(descripcion, monto, haceDias, null);
    }

    private void gasto(String descripcion, String monto, int haceDias, Categoria categoria) {
        gastos.add(new Gasto(UUID.randomUUID().toString(), descripcion, new BigDecimal(monto), HOY.minusDays(haceDias),
                categoria == null ? null : categoria.id(), null, null, null, AHORA, AHORA, false));
    }

    /** Gastos variados (cada uno distinto) para pasar el mínimo de datos sin crear hábitos. */
    private void rellenar() {
        for (int i = 0; i < 8; i++) {
            gasto("Varios " + i, "10", 50 + i);
        }
    }

    private static Analisis.IdeaAhorro idea(Analisis a, Analisis.IdeaAhorro.Tipo tipo, String sobre) {
        return a.ideas().stream().filter(i -> i.tipo() == tipo && i.sobre().equals(sobre)).findFirst().orElseThrow();
    }
}
