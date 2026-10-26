package com.registrogastos.persistencia;

import com.registrogastos.dominio.Categoria;
import com.registrogastos.dominio.Gasto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class GastoRepositorioTest {

    private static final Instant AHORA = Instant.parse("2026-10-25T12:00:00Z");

    @TempDir
    Path carpeta; // JUnit crea una carpeta nueva por prueba y la borra al final

    private BaseDeDatos baseDeDatos;
    private GastoRepositorio gastos;
    private CategoriaRepositorio categorias;

    @BeforeEach
    void preparar() {
        baseDeDatos = new BaseDeDatos(carpeta.resolve("prueba.db"));
        new Migrador(baseDeDatos).migrar();
        gastos = new GastoRepositorio(baseDeDatos);
        categorias = new CategoriaRepositorio(baseDeDatos);
    }

    @Test
    void migrarDosVecesNoFallaYDejaLaUltimaVersion() {
        var migrador = new Migrador(baseDeDatos);
        migrador.migrar();
        assertEquals(6, migrador.versionActual());
    }

    @Test
    void guardaYRecuperaSinPerderCentavos() {
        var comida = Categoria.nueva("Comida", "#66C0F4", AHORA);
        categorias.guardar(comida);
        var gasto = Gasto.nuevo("Almuerzo", new BigDecimal("12.3"), LocalDate.of(2026, 10, 25), comida.id(), AHORA);

        gastos.guardar(gasto);

        var leido = gastos.buscarPorId(gasto.id()).orElseThrow();
        assertEquals(gasto, leido); // records: equals compara todos los campos
        assertEquals(new BigDecimal("12.30"), leido.monto());
        assertEquals("#66c0f4", categorias.buscarPorId(comida.id()).orElseThrow().color()); // normalizado
    }

    @Test
    void guardarConElMismoIdActualiza() {
        var gasto = Gasto.nuevo("Taxi", new BigDecimal("5"), LocalDate.of(2026, 10, 1), null, AHORA);
        gastos.guardar(gasto);

        gastos.guardar(gasto.editar("Taxi aeropuerto", new BigDecimal("15"), gasto.fecha(), null, AHORA.plusSeconds(60)));

        var leido = gastos.buscarPorId(gasto.id()).orElseThrow();
        assertEquals("Taxi aeropuerto", leido.descripcion());
        assertEquals(AHORA, leido.creadoEn());
        assertEquals(AHORA.plusSeconds(60), leido.actualizadoEn());
    }

    @Test
    void listarEntreExcluyeEliminadosYFechasFueraDeRango() {
        var octubre = Gasto.nuevo("Octubre", BigDecimal.ONE, LocalDate.of(2026, 10, 31), null, AHORA);
        var noviembre = Gasto.nuevo("Noviembre", BigDecimal.ONE, LocalDate.of(2026, 11, 1), null, AHORA);
        var borrado = Gasto.nuevo("Borrado", BigDecimal.ONE, LocalDate.of(2026, 10, 2), null, AHORA);
        gastos.guardar(octubre);
        gastos.guardar(noviembre);
        gastos.guardar(borrado.eliminar(AHORA));

        var lista = gastos.listarEntre(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertEquals(1, lista.size());
        assertEquals("Octubre", lista.getFirst().descripcion());
    }

    @Test
    void lasLlavesForaneasEstanActivas() {
        var huerfano = Gasto.nuevo("Sin dueño", BigDecimal.ONE, LocalDate.now(), "categoria-inexistente", AHORA);
        assertThrows(ErrorDePersistencia.class, () -> gastos.guardar(huerfano));
    }
}
