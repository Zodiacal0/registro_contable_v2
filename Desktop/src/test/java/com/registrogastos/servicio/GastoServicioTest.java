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
import java.util.Set;
import java.util.stream.Stream;

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
    void resumenAgrupaPorCategoriaYComparaConElMesAnterior() {
        var comida = servicio.crearCategoria("Comida");
        servicio.registrar("Súper", new BigDecimal("60"), LocalDate.of(2026, 10, 2), comida.id());
        servicio.registrar("Almuerzo", new BigDecimal("15"), LocalDate.of(2026, 10, 20), comida.id());
        servicio.registrar("Regalo", new BigDecimal("25"), LocalDate.of(2026, 10, 21), null);
        servicio.registrar("Septiembre", new BigDecimal("80"), LocalDate.of(2026, 9, 15), null);

        var resumen = servicio.resumen(YearMonth.of(2026, 10));

        assertEquals(new BigDecimal("100.00"), resumen.total());
        assertEquals(new BigDecimal("25.0"), resumen.variacion().orElseThrow()); // 100 vs 80
        assertEquals(new BigDecimal("4.00"), resumen.promedioDiario());          // reloj en el día 25
        assertEquals("Súper", resumen.mayorGasto().orElseThrow().descripcion());

        var primera = resumen.porCategoria().getFirst();
        assertEquals("Comida", primera.categoria().nombre());
        assertEquals(new BigDecimal("75.00"), primera.total());
        assertEquals(0.75, primera.proporcion(), 1e-9);
        assertEquals(2, primera.movimientos());
        assertTrue(resumen.porCategoria().get(1).sinCategoria());

        var porDia = resumen.porDia();
        assertEquals(31, porDia.size());
        assertEquals(new BigDecimal("60.00"), porDia.get(1));   // día 2
        assertEquals(BigDecimal.ZERO, porDia.get(2));            // día 3, sin gastos
    }

    @Test
    void sinGastosElMesAnteriorNoHayVariacion() {
        servicio.registrar("Café", BigDecimal.ONE, LocalDate.of(2026, 10, 1), null);
        assertTrue(servicio.resumen(YearMonth.of(2026, 10)).variacion().isEmpty());
    }

    @Test
    void eliminarCategoriaDejaSusGastosSinCategoria() {
        var ocio = servicio.crearCategoria("Ocio");
        servicio.registrar("Cine", new BigDecimal("8"), LocalDate.of(2026, 10, 10), ocio.id());

        servicio.eliminarCategoria(ocio.id());

        var resumen = servicio.resumen(YearMonth.of(2026, 10));
        assertTrue(resumen.categorias().isEmpty());
        assertTrue(resumen.porCategoria().getFirst().sinCategoria());
    }

    @Test
    void cadaCategoriaNuevaTomaUnColorLibre() {
        var colores = Stream.of("A", "B", "C", "D", "E")
                .map(nombre -> servicio.crearCategoria(nombre).color())
                .toList();
        assertEquals(colores.size(), Set.copyOf(colores).size());
    }

    @Test
    void elDominioRechazaMontosInvalidos() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrar("X", new BigDecimal("-1"), LocalDate.of(2026, 10, 1), null));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrar("X", new BigDecimal("1.999"), LocalDate.of(2026, 10, 1), null));
    }
}
