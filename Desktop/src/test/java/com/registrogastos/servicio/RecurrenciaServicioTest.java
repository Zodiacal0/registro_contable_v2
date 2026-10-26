package com.registrogastos.servicio;

import com.registrogastos.dominio.Recurrencia;
import com.registrogastos.persistencia.BaseDeDatos;
import com.registrogastos.persistencia.CategoriaRepositorio;
import com.registrogastos.persistencia.GastoRepositorio;
import com.registrogastos.persistencia.Migrador;
import com.registrogastos.persistencia.RecurrenciaRepositorio;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecurrenciaServicioTest {

    // "Hoy" es el 25 de octubre de 2026
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path carpeta;

    private RecurrenciaServicio recurrencias;
    private GastoServicio gastos;

    @BeforeEach
    void preparar() {
        var baseDeDatos = new BaseDeDatos(carpeta.resolve("prueba.db"));
        new Migrador(baseDeDatos).migrar();
        var gastoRepo = new GastoRepositorio(baseDeDatos);
        var categoriaRepo = new CategoriaRepositorio(baseDeDatos);
        gastos = new GastoServicio(gastoRepo, categoriaRepo, RELOJ);
        recurrencias = new RecurrenciaServicio(new RecurrenciaRepositorio(baseDeDatos), gastoRepo, categoriaRepo, RELOJ);
    }

    private Recurrencia netflixDesdeAgosto() {
        return recurrencias.crear("Netflix", new BigDecimal("15.49"), null, 1, Recurrencia.Unidad.MESES,
                LocalDate.of(2026, 8, 5));
    }

    @Test
    void alCrearRegistraLosCobrosQueYaVencieron() {
        netflixDesdeAgosto();
        // 5 ago, 5 sep, 5 oct (el 5 de noviembre todavía no llega)
        assertEquals(1, gastos.delMes(YearMonth.of(2026, 10)).size());
        assertEquals(1, gastos.delMes(YearMonth.of(2026, 8)).size());
        assertTrue(gastos.delMes(YearMonth.of(2026, 10)).getFirst().esRecurrente());
    }

    @Test
    void generarDosVecesNoDuplica() {
        netflixDesdeAgosto();
        assertEquals(0, recurrencias.generarPendientes());
        assertEquals(1, gastos.delMes(YearMonth.of(2026, 9)).size());
    }

    @Test
    void unCobroBorradoNoRevive() {
        var netflix = netflixDesdeAgosto();
        var septiembre = gastos.delMes(YearMonth.of(2026, 9)).getFirst();
        gastos.eliminar(septiembre.id());

        // Aunque la recurrencia "olvide" hasta dónde generó, el cobro borrado sigue borrado
        new RecurrenciaRepositorio(new BaseDeDatos(carpeta.resolve("prueba.db")))
                .guardar(netflix.generadaHasta(null, RELOJ.instant()));
        recurrencias.generarPendientes();

        assertTrue(gastos.delMes(YearMonth.of(2026, 9)).isEmpty());
    }

    @Test
    void enPausaNoGeneraYAlReanudarSeSaltaLoVencido() {
        var gym = recurrencias.crear("Gimnasio", new BigDecimal("30"), null, 1, Recurrencia.Unidad.MESES,
                LocalDate.of(2026, 11, 1)); // primer cobro en el futuro: todavía nada
        recurrencias.pausar(gym.id());
        recurrencias.generarPendientes();
        assertTrue(recurrencias.cobrosDe(gym.id()).isEmpty());

        recurrencias.reanudar(gym.id());
        assertEquals(1, recurrencias.proximos(30).size()); // el 1 de noviembre
    }

    @Test
    void costoMensualSumaSoloLasActivas() {
        netflixDesdeAgosto();
        var dominio = recurrencias.crear("Dominio", new BigDecimal("120"), null, 12, Recurrencia.Unidad.MESES,
                LocalDate.of(2026, 3, 1));
        assertEquals(new BigDecimal("25.49"), recurrencias.costoMensual()); // 15.49 + 120/12

        recurrencias.pausar(dominio.id());
        assertEquals(new BigDecimal("15.49"), recurrencias.costoMensual());
    }

    @Test
    void moverElPrimerCobroRehaceElHistorial() {
        var netflix = netflixDesdeAgosto(); // cobros: 5 ago, 5 sep, 5 oct

        // En realidad empezó el 20 de junio: 20 jun, 20 jul, 20 ago, 20 sep, 20 oct
        recurrencias.editar(netflix.id(), "Netflix", new BigDecimal("15.49"), null, 1, Recurrencia.Unidad.MESES,
                LocalDate.of(2026, 6, 20));

        var cobros = recurrencias.cobrosDe(netflix.id());
        assertEquals(5, cobros.size());
        assertTrue(cobros.stream().allMatch(g -> g.fecha().getDayOfMonth() == 20)); // los del día 5 se quitaron
        assertEquals(1, gastos.delMes(YearMonth.of(2026, 10)).size());

        // Volver a guardar con el mismo inicio no toca nada
        recurrencias.editar(netflix.id(), "Netflix", new BigDecimal("15.49"), null, 1, Recurrencia.Unidad.MESES,
                LocalDate.of(2026, 6, 20));
        assertEquals(5, recurrencias.cobrosDe(netflix.id()).size());
    }
}
