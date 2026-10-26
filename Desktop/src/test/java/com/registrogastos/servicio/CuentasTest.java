package com.registrogastos.servicio;

import com.registrogastos.dominio.Cuenta;
import com.registrogastos.dominio.Moneda;
import com.registrogastos.persistencia.AjustesRepositorio;
import com.registrogastos.persistencia.BaseDeDatos;
import com.registrogastos.persistencia.CategoriaRepositorio;
import com.registrogastos.persistencia.CuentaRepositorio;
import com.registrogastos.persistencia.GastoRepositorio;
import com.registrogastos.persistencia.IngresoRepositorio;
import com.registrogastos.persistencia.Migrador;
import com.registrogastos.persistencia.TransferenciaRepositorio;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

class CuentasTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate HOY = LocalDate.of(2026, 10, 25);

    @TempDir
    Path carpeta;

    private CuentaServicio cuentas;
    private GastoServicio gastos;

    @BeforeEach
    void preparar() {
        var bd = new BaseDeDatos(carpeta.resolve("prueba.db"));
        new Migrador(bd).migrar();
        var gastoRepo = new GastoRepositorio(bd);
        cuentas = new CuentaServicio(new CuentaRepositorio(bd), new IngresoRepositorio(bd),
                new TransferenciaRepositorio(bd), gastoRepo, new AjustesRepositorio(bd), RELOJ);
        gastos = new GastoServicio(gastoRepo, new CategoriaRepositorio(bd), cuentas::conversor, RELOJ);
    }

    @Test
    void laMigracionCreaLaCuentaPrincipalEnQuetzalesYElTipoDeCambio() {
        var principal = cuentas.listar().getFirst().cuenta();
        assertEquals(Cuenta.PRINCIPAL_ID, principal.id());
        assertEquals(Moneda.GTQ, principal.moneda());
        assertEquals(new BigDecimal("7.70"), cuentas.tipoCambio(Moneda.USD));
    }

    @Test
    void elSaldoSumaIngresosYRestaGastosYTransferencias() {
        var banco = cuentas.crear("Banco", Cuenta.Tipo.BANCO, Moneda.GTQ, new BigDecimal("1000"));
        cuentas.registrarIngreso(banco.id(), "Salario", new BigDecimal("500"), HOY);
        gastos.registrar("Súper", new BigDecimal("120.50"), HOY, null, banco.id());
        cuentas.transferir(banco.id(), Cuenta.PRINCIPAL_ID, new BigDecimal("200"), null, HOY);

        assertEquals(new BigDecimal("1179.50"), saldoDe(banco.id()));
        assertEquals(new BigDecimal("200.00"), saldoDe(Cuenta.PRINCIPAL_ID));
        assertEquals(3, cuentas.movimientos(banco.id()).size());
    }

    @Test
    void transferirEntreMonedasConvierteConElTipoDeCambio() {
        var dolares = cuentas.crear("Dólares", Cuenta.Tipo.BANCO, Moneda.USD, BigDecimal.ZERO);
        cuentas.transferir(Cuenta.PRINCIPAL_ID, dolares.id(), new BigDecimal("770"), null, HOY);

        assertEquals(new BigDecimal("100.00"), saldoDe(dolares.id()));
        assertEquals(new BigDecimal("-770.00"), saldoDe(Cuenta.PRINCIPAL_ID));
    }

    @Test
    void elResumenSumaLosGastosEnDolaresConvertidosAQuetzales() {
        var dolares = cuentas.crear("Dólares", Cuenta.Tipo.TARJETA, Moneda.USD, BigDecimal.ZERO);
        gastos.registrar("Netflix", new BigDecimal("10"), HOY, null, dolares.id());
        gastos.registrar("Almuerzo", new BigDecimal("20"), HOY, null);

        var resumen = gastos.resumen(YearMonth.of(2026, 10));
        assertEquals(new BigDecimal("97.00"), resumen.total()); // 10 × 7.70 + 20
        assertEquals(new BigDecimal("77.00"), resumen.mayorGasto().map(resumen::enPrincipal).orElseThrow());

        cuentas.fijarTipoCambio(Moneda.USD, new BigDecimal("8"));
        assertEquals(new BigDecimal("100.00"), gastos.resumen(YearMonth.of(2026, 10)).total());
    }

    @Test
    void laCuentaPrincipalNoSeEliminaYElTipoDeCambioDebeSerPositivo() {
        assertThrows(IllegalArgumentException.class, () -> cuentas.eliminar(Cuenta.PRINCIPAL_ID));
        assertThrows(IllegalArgumentException.class, () -> cuentas.fijarTipoCambio(Moneda.USD, BigDecimal.ZERO));
    }

    private BigDecimal saldoDe(String id) {
        return cuentas.listar().stream().filter(e -> e.cuenta().id().equals(id)).findFirst().orElseThrow().saldo();
    }
}
