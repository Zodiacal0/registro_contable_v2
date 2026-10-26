package com.registrogastos.servicio;

import com.registrogastos.dominio.Cuenta;
import com.registrogastos.dominio.LineaPlan;
import com.registrogastos.dominio.Recurrencia;
import com.registrogastos.persistencia.AjustesRepositorio;
import com.registrogastos.persistencia.BaseDeDatos;
import com.registrogastos.persistencia.CategoriaRepositorio;
import com.registrogastos.persistencia.CuentaRepositorio;
import com.registrogastos.persistencia.GastoRepositorio;
import com.registrogastos.persistencia.IngresoRepositorio;
import com.registrogastos.persistencia.LineaPlanRepositorio;
import com.registrogastos.persistencia.Migrador;
import com.registrogastos.persistencia.RecurrenciaRepositorio;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresupuestoTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate HOY = LocalDate.of(2026, 10, 25);
    private static final YearMonth MES = YearMonth.of(2026, 10);

    @TempDir
    Path carpeta;

    private GastoServicio gastos;
    private RecurrenciaServicio recurrencias;
    private CuentaServicio cuentas;
    private PresupuestoServicio presupuesto;

    @BeforeEach
    void preparar() {
        var bd = new BaseDeDatos(carpeta.resolve("prueba.db"));
        new Migrador(bd).migrar();
        var gastoRepo = new GastoRepositorio(bd);
        var categoriaRepo = new CategoriaRepositorio(bd);
        var ingresos = new IngresoRepositorio(bd);
        var ajustes = new AjustesRepositorio(bd);
        cuentas = new CuentaServicio(new CuentaRepositorio(bd), ingresos, new TransferenciaRepositorio(bd), gastoRepo,
                ajustes, RELOJ);
        gastos = new GastoServicio(gastoRepo, categoriaRepo, cuentas::conversor, RELOJ);
        recurrencias = new RecurrenciaServicio(new RecurrenciaRepositorio(bd), gastoRepo, categoriaRepo, RELOJ);
        presupuesto = new PresupuestoServicio(new LineaPlanRepositorio(bd), ajustes, ingresos, gastos, recurrencias,
                cuentas::conversor, RELOJ);
    }

    @Test
    void elSueldoVieneDelAjusteOSiNoDeLoRegistradoEnCuentas() {
        cuentas.registrarIngreso(Cuenta.PRINCIPAL_ID, "Salario", new BigDecimal("6000"), HOY.withDayOfMonth(1));
        var plan = presupuesto.plan(MES);
        assertFalse(plan.ingresoFijado());
        assertEquals(new BigDecimal("6000.00"), plan.ingresoEsperado());

        presupuesto.fijarIngresoMensual(new BigDecimal("6500"));
        plan = presupuesto.plan(MES);
        assertTrue(plan.ingresoFijado());
        assertEquals(new BigDecimal("6500"), plan.ingresoEsperado());
        assertThrows(IllegalArgumentException.class, () -> presupuesto.fijarIngresoMensual(new BigDecimal("-1")));
    }

    @Test
    void losGastosDelPlanDicenQueParteDelSueldoSeLlevanYCuantoQueda() {
        presupuesto.fijarIngresoMensual(new BigDecimal("5000"));
        presupuesto.agregar("Renta", new BigDecimal("2000"), LineaPlan.Grupo.GASTO);
        presupuesto.agregar("Comida", new BigDecimal("1500"), LineaPlan.Grupo.GASTO);

        var plan = presupuesto.plan(MES);
        assertEquals(2, plan.gastos().size());
        assertEquals("Renta", plan.gastos().getFirst().nombre()); // en el orden en que se agregaron
        assertEquals(new BigDecimal("3500.00"), plan.totalGastos());
        assertEquals(0.4, plan.parte(new BigDecimal("2000")), 0.0001);
        assertEquals(new BigDecimal("1500.00"), plan.disponible());
    }

    @Test
    void loDisponibleSeAsignaHastaQueNoQuedeNada() {
        presupuesto.fijarIngresoMensual(new BigDecimal("5000"));
        presupuesto.agregar("Renta", new BigDecimal("3500"), LineaPlan.Grupo.GASTO);
        var ahorro = presupuesto.agregar("Ahorro", new BigDecimal("1000"), LineaPlan.Grupo.ASIGNACION);

        var plan = presupuesto.plan(MES);
        assertEquals(new BigDecimal("1000.00"), plan.totalAsignado());
        assertEquals(new BigDecimal("500.00"), plan.sinAsignar());

        presupuesto.editar(ahorro.id(), "Ahorro", new BigDecimal("1500"));
        assertEquals(new BigDecimal("0.00"), presupuesto.plan(MES).sinAsignar());

        presupuesto.eliminar(ahorro.id());
        assertTrue(presupuesto.plan(MES).asignaciones().isEmpty());
    }

    @Test
    void traerRecurrentesAgregaLasQueFaltanConSuCostoMensual() {
        recurrencias.crear("Netflix", new BigDecimal("15.49"), null, 1, Recurrencia.Unidad.MESES, HOY);
        recurrencias.crear("Dominio", new BigDecimal("120"), null, 12, Recurrencia.Unidad.MESES, HOY);
        presupuesto.agregar("netflix", new BigDecimal("16"), LineaPlan.Grupo.GASTO); // ya está, aunque en minúsculas

        assertEquals(1, presupuesto.plan(MES).sugeridas().size());
        assertEquals(1, presupuesto.traerRecurrentes());

        var plan = presupuesto.plan(MES);
        assertEquals(new BigDecimal("10.00"), plan.gastos().getLast().monto()); // 120 / 12
        assertTrue(plan.sugeridas().isEmpty());
        assertEquals(0, presupuesto.traerRecurrentes()); // no duplica
    }

    @Test
    void porQuincenaElSueldoSePartYCadaLineaCaeDondeSeMarque() {
        presupuesto.fijarIngresoMensual(new BigDecimal("6000"));
        presupuesto.fijarQuincenal(true);
        var renta = presupuesto.agregar("Renta", new BigDecimal("2000"), LineaPlan.Grupo.GASTO);
        presupuesto.agregar("Comida", new BigDecimal("1000"), LineaPlan.Grupo.GASTO); // mitad y mitad
        presupuesto.cambiarQuincena(renta.id(), LineaPlan.Quincena.PRIMERA);
        var ahorro = presupuesto.agregar("Ahorro", new BigDecimal("600"), LineaPlan.Grupo.ASIGNACION);
        presupuesto.cambiarQuincena(ahorro.id(), LineaPlan.Quincena.SEGUNDA);

        var plan = presupuesto.plan(MES);
        assertTrue(plan.quincenal());
        assertEquals(new BigDecimal("3000.00"), plan.sueldoQuincena());
        assertEquals(new BigDecimal("2500.00"), plan.gastosEn(LineaPlan.Quincena.PRIMERA)); // 2000 + 500
        assertEquals(new BigDecimal("500.00"), plan.gastosEn(LineaPlan.Quincena.SEGUNDA));
        assertEquals(new BigDecimal("500.00"), plan.disponibleEn(LineaPlan.Quincena.PRIMERA));
        assertEquals(new BigDecimal("1900.00"), plan.sinAsignarEn(LineaPlan.Quincena.SEGUNDA)); // 2500 - 600
        // El total del mes no cambia
        assertEquals(new BigDecimal("3000.00"), plan.disponible());
        assertEquals(1, plan.lineasEn(LineaPlan.Quincena.SEGUNDA, LineaPlan.Grupo.GASTO).size()); // solo la comida
    }

    @Test
    void loGastadoDeVerdadSeParteEnQuincenasPorElDia() {
        gastos.registrar("Súper", new BigDecimal("300"), HOY.withDayOfMonth(15), null);
        gastos.registrar("Cine", new BigDecimal("50"), HOY.withDayOfMonth(16), null);
        var plan = presupuesto.plan(MES);
        assertEquals(new BigDecimal("300.00"), plan.gastadoRealEn(LineaPlan.Quincena.PRIMERA));
        assertEquals(new BigDecimal("50.00"), plan.gastadoRealEn(LineaPlan.Quincena.SEGUNDA));
    }

    @Test
    void elPlanMuestraLoGastadoDeVerdadParaComparar() {
        gastos.registrar("Súper", new BigDecimal("320.50"), HOY, null);
        assertEquals(new BigDecimal("320.50"), presupuesto.plan(MES).gastadoReal());
    }
}
