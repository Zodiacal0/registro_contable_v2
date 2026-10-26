package com.registrogastos.dominio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecurrenciaTest {

    private static final Instant AHORA = Instant.parse("2026-10-25T12:00:00Z");

    private static Recurrencia cada(int n, Recurrencia.Unidad unidad, LocalDate inicio) {
        return Recurrencia.nueva("Prueba", new BigDecimal("10"), null, n, unidad, inicio, AHORA);
    }

    @Test
    void mensualDesdeEl31NoSeQuedaEnEl28() {
        var r = cada(1, Recurrencia.Unidad.MESES, LocalDate.of(2026, 1, 31));
        // Febrero no tiene 31: cae en 28; pero marzo vuelve al 31 porque se calcula desde el inicio
        assertEquals(List.of(
                        LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28),
                        LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)),
                r.cobrosEntre(null, LocalDate.of(2026, 4, 30)));
    }

    @Test
    void anualYCadaQuinceDias() {
        var anual = cada(12, Recurrencia.Unidad.MESES, LocalDate.of(2024, 3, 10));
        assertEquals(List.of(LocalDate.of(2025, 3, 10), LocalDate.of(2026, 3, 10)),
                anual.cobrosEntre(LocalDate.of(2024, 3, 10), LocalDate.of(2026, 10, 25)));

        var quincenal = cada(15, Recurrencia.Unidad.DIAS, LocalDate.of(2026, 10, 1));
        assertEquals(LocalDate.of(2026, 10, 31), quincenal.proximoCobro(LocalDate.of(2026, 10, 17)));
    }

    @Test
    void costoMensualNormalizaLaFrecuencia() {
        assertEquals(new BigDecimal("10.00"), cada(1, Recurrencia.Unidad.MESES, LocalDate.now()).costoMensual());
        assertEquals(new BigDecimal("0.83"), cada(12, Recurrencia.Unidad.MESES, LocalDate.now()).costoMensual());
        // cada 30 días: 10 / 30 * 30.4375
        assertEquals(new BigDecimal("10.15"), cada(30, Recurrencia.Unidad.DIAS, LocalDate.now()).costoMensual());
    }

    @Test
    void elIdDelCobroEsDeterministico() {
        var r = cada(1, Recurrencia.Unidad.MESES, LocalDate.of(2026, 1, 5));
        var fecha = LocalDate.of(2026, 10, 5);
        assertEquals(r.idGasto(fecha), r.idGasto(fecha));              // mismo cobro, mismo id
        assertNotEquals(r.idGasto(fecha), r.idGasto(fecha.plusMonths(1)));
    }

    @Test
    void validaFrecuenciaYMonto() {
        assertThrows(IllegalArgumentException.class, () -> cada(0, Recurrencia.Unidad.MESES, LocalDate.now()));
        assertThrows(IllegalArgumentException.class, () -> Recurrencia.nueva("X", BigDecimal.ZERO, null,
                1, Recurrencia.Unidad.MESES, LocalDate.now(), AHORA));
    }
}
