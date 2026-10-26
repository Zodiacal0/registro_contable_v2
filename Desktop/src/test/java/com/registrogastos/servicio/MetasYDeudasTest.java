package com.registrogastos.servicio;

import com.registrogastos.dominio.Deuda;
import com.registrogastos.persistencia.AbonoRepositorio;
import com.registrogastos.persistencia.BaseDeDatos;
import com.registrogastos.persistencia.CategoriaRepositorio;
import com.registrogastos.persistencia.DeudaRepositorio;
import com.registrogastos.persistencia.GastoRepositorio;
import com.registrogastos.persistencia.MetaRepositorio;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetasYDeudasTest {

    // "Hoy" es el 25 de octubre de 2026
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate HOY = LocalDate.of(2026, 10, 25);

    @TempDir
    Path carpeta;

    private GastoServicio gastos;
    private MetaServicio metas;
    private DeudaServicio deudas;

    @BeforeEach
    void preparar() {
        var bd = new BaseDeDatos(carpeta.resolve("prueba.db"));
        new Migrador(bd).migrar();
        var gastoRepo = new GastoRepositorio(bd);
        var categoriaRepo = new CategoriaRepositorio(bd);
        gastos = new GastoServicio(gastoRepo, categoriaRepo, RELOJ);
        metas = new MetaServicio(new MetaRepositorio(bd), AbonoRepositorio.aportes(bd), RELOJ);
        deudas = new DeudaServicio(new DeudaRepositorio(bd), AbonoRepositorio.cobrosDeuda(bd), gastoRepo,
                categoriaRepo, RELOJ);
    }

    // ---- Metas ----

    @Test
    void elProgresoDeUnaMetaSaleDeSusAportesYRetiros() {
        var laptop = metas.crear("Laptop", new BigDecimal("1200"), LocalDate.of(2027, 1, 15));
        metas.aportar(laptop.id(), new BigDecimal("500"), HOY);
        metas.retirar(laptop.id(), new BigDecimal("100"), HOY);

        var estado = metas.listar().getFirst();
        assertEquals(new BigDecimal("400.00"), estado.ahorrado());
        assertEquals(new BigDecimal("800.00"), estado.restante());
        // Faltan octubre, noviembre, diciembre y enero: 800 / 4
        assertEquals(new BigDecimal("200.00"), estado.aporteMensualSugerido().orElseThrow());
    }

    @Test
    void losAportesNoSonGastos() {
        var meta = metas.crear("Viaje", new BigDecimal("1000"), null);
        metas.aportar(meta.id(), new BigDecimal("300"), HOY);
        assertTrue(gastos.delMes(YearMonth.of(2026, 10)).isEmpty());
    }

    @Test
    void noSePuedeRetirarMasDeLoAhorrado() {
        var meta = metas.crear("Bici", new BigDecimal("450"), null);
        metas.aportar(meta.id(), new BigDecimal("100"), HOY);
        assertThrows(IllegalArgumentException.class, () -> metas.retirar(meta.id(), new BigDecimal("150"), HOY));
    }

    @Test
    void unaMetaAlcanzadaQuedaCompletadaYSinSugerencia() {
        var meta = metas.crear("Bici", new BigDecimal("450"), LocalDate.of(2027, 3, 1));
        metas.aportar(meta.id(), new BigDecimal("450"), HOY);
        var estado = metas.listar().getFirst();
        assertTrue(estado.completada());
        assertTrue(estado.aporteMensualSugerido().isEmpty());
    }

    // ---- Deudas ----

    @Test
    void abonarLoQueDeboEsUnGastoYBajaElSaldo() {
        var tarjeta = deudas.crear("Tarjeta", Deuda.Tipo.DEBO, new BigDecimal("1000"), HOY.minusMonths(1), null);
        deudas.abonar(tarjeta.id(), new BigDecimal("250"), HOY, null);

        var mes = gastos.delMes(YearMonth.of(2026, 10));
        assertEquals(1, mes.size());
        assertTrue(mes.getFirst().esAbono());
        assertEquals(new BigDecimal("750.00"), deudas.listar().getFirst().saldo());
    }

    @Test
    void elAbonoDesdeElPanelUsaLaDescripcionDelUsuarioOLaDeLaDeuda() {
        var tarjeta = deudas.crear("Tarjeta", Deuda.Tipo.DEBO, new BigDecimal("1000"), HOY, null);
        String propio = deudas.abonar(tarjeta.id(), new BigDecimal("100"), HOY, null, "Cuota octubre", null);
        String vacio = deudas.abonar(tarjeta.id(), new BigDecimal("50"), HOY, null, "  ", null);

        var mes = gastos.delMes(YearMonth.of(2026, 10));
        assertEquals("Cuota octubre", mes.stream().filter(g -> g.id().equals(propio)).findFirst().orElseThrow().descripcion());
        assertEquals("Abono · Tarjeta", mes.stream().filter(g -> g.id().equals(vacio)).findFirst().orElseThrow().descripcion());
        assertEquals(new BigDecimal("850.00"), deudas.listar().getFirst().saldo());
    }

    @Test
    void borrarElGastoDelAbonoDevuelveElSaldo() {
        var tarjeta = deudas.crear("Tarjeta", Deuda.Tipo.DEBO, new BigDecimal("1000"), HOY, null);
        deudas.abonar(tarjeta.id(), new BigDecimal("250"), HOY, null);

        // Se borra desde Movimientos, como un gasto cualquiera: una sola fuente de verdad
        gastos.eliminar(gastos.delMes(YearMonth.of(2026, 10)).getFirst().id());

        assertEquals(new BigDecimal("1000.00"), deudas.listar().getFirst().saldo());
    }

    @Test
    void loQueMeDebenNoGeneraGastos() {
        var carlos = deudas.crear("Carlos", Deuda.Tipo.ME_DEBEN, new BigDecimal("120"), HOY, null);
        deudas.abonar(carlos.id(), new BigDecimal("40"), HOY, null);

        assertTrue(gastos.delMes(YearMonth.of(2026, 10)).isEmpty());
        assertEquals(new BigDecimal("80.00"), deudas.listar().getFirst().saldo());
        assertEquals(1, deudas.abonosDe(carlos.id()).size());
    }

    @Test
    void noSePuedeAbonarMasDelSaldoYAlPagarTodoQuedaSaldada() {
        var prestamo = deudas.crear("Préstamo", Deuda.Tipo.DEBO, new BigDecimal("500"), HOY, null);
        assertThrows(IllegalArgumentException.class,
                () -> deudas.abonar(prestamo.id(), new BigDecimal("600"), HOY, null));

        deudas.abonar(prestamo.id(), new BigDecimal("500"), HOY, null);
        assertTrue(deudas.listar().getFirst().saldada());
        assertFalse(deudas.listar().getFirst().saldo().signum() > 0);
    }
}
