package com.registrogastos.servicio;

import com.registrogastos.dominio.Gasto;
import com.registrogastos.persistencia.BaseDeDatos;
import com.registrogastos.persistencia.CategoriaRepositorio;
import com.registrogastos.persistencia.GastoRepositorio;
import com.registrogastos.persistencia.Migrador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class GastoServicioTest {

    // Reloj fijo: las pruebas no dependen del día en que se corren
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path carpeta;

    private GastoServicio servicio;

    @BeforeEach
    void preparar() {
        var baseDeDatos = new BaseDeDatos(carpeta.resolve("prueba.db"));
        new Migrador(baseDeDatos).migrar();
        servicio = new GastoServicio(new GastoRepositorio(baseDeDatos), new CategoriaRepositorio(baseDeDatos), RELOJ);
    }

    @Test
    void totalDelMesSumaExacto() {
        servicio.registrar("Café", new BigDecimal("0.10"), LocalDate.of(2026, 10, 3), null);
        servicio.registrar("Pan", new BigDecimal("0.20"), LocalDate.of(2026, 10, 4), null);

        var delMes = servicio.delMes(YearMonth.of(2026, 10));

        assertEquals(new BigDecimal("0.30"), servicio.total(delMes)); // con double daría 0.30000000000000004
    }

    @Test
    void eliminarEsLogicoYSaleDelListado() {
        Gasto gasto = servicio.registrar("Cine", new BigDecimal("8"), LocalDate.of(2026, 10, 10), null);

        servicio.eliminar(gasto.id());

        assertTrue(servicio.delMes(servicio.mesActual()).isEmpty());
    }

    @Test
    void noPermiteCategoriaInexistente() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrar("X", BigDecimal.ONE, LocalDate.of(2026, 10, 1), "no-existe"));
    }

    @Test
    void elDominioRechazaMontosInvalidos() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrar("X", new BigDecimal("-1"), LocalDate.of(2026, 10, 1), null));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrar("X", new BigDecimal("1.999"), LocalDate.of(2026, 10, 1), null));
    }
}
