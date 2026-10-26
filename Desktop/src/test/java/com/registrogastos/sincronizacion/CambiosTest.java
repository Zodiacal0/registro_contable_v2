package com.registrogastos.sincronizacion;

import com.registrogastos.dominio.Gasto;
import com.registrogastos.persistencia.AjustesRepositorio;
import com.registrogastos.persistencia.BaseDeDatos;
import com.registrogastos.persistencia.CategoriaRepositorio;
import com.registrogastos.persistencia.GastoRepositorio;
import com.registrogastos.persistencia.Migrador;
import com.registrogastos.servicio.GastoServicio;
import org.json.JSONObject;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dos bases (la PC y el teléfono) que se intercambian cambios. */
class CambiosTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 25);
    private static final YearMonth MES = YearMonth.of(2026, 10);

    @TempDir
    Path carpeta;

    private Lado pc;
    private Lado telefono;

    /** Un dispositivo: su base, sus servicios y un reloj propio que se puede adelantar. */
    private static final class Lado {
        Instant ahora = Instant.parse("2026-10-25T12:00:00Z");
        final GastoServicio gastos;
        final Cambios cambios;
        final AjustesRepositorio ajustes;

        Lado(Path archivo) {
            var bd = new BaseDeDatos(archivo);
            ajustes = new AjustesRepositorio(bd);
            new Migrador(bd).migrar();
            Clock reloj = new Clock() {
                @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(java.time.ZoneId zona) { return this; }
                @Override public Instant instant() { return ahora; }
            };
            gastos = new GastoServicio(new GastoRepositorio(bd), new CategoriaRepositorio(bd), reloj);
            cambios = new Cambios(bd);
        }

        void avanzar(int segundos) {
            ahora = ahora.plusSeconds(segundos);
        }
    }

    @BeforeEach
    void preparar() {
        pc = new Lado(carpeta.resolve("pc.db"));
        telefono = new Lado(carpeta.resolve("telefono.db"));
    }

    /** Lo que haría una sincronización: cada lado aplica lo del otro. */
    private static void sincronizar(Lado a, Lado b, long desde) {
        JSONObject deA = a.cambios.desde(desde);
        JSONObject deB = b.cambios.desde(desde);
        b.cambios.aplicar(deA);
        a.cambios.aplicar(deB);
    }

    @Test
    void loRegistradoEnUnLadoApareceEnElOtro() {
        var comida = pc.gastos.crearCategoria("Comida");
        pc.gastos.registrar("Súper", new BigDecimal("80"), HOY, comida.id());
        telefono.gastos.registrar("Café", new BigDecimal("4.50"), HOY, null);

        sincronizar(pc, telefono, 0);

        assertEquals(2, pc.gastos.delMes(MES).size());
        assertEquals(2, telefono.gastos.delMes(MES).size());
        assertEquals("Comida", telefono.gastos.categorias().getFirst().nombre());
    }

    @Test
    void siLosDosEditanLoMismoGanaElCambioMasReciente() {
        Gasto gasto = pc.gastos.registrar("Súper", new BigDecimal("80"), HOY, null);
        sincronizar(pc, telefono, 0);

        pc.avanzar(10);
        pc.gastos.editar(gasto.id(), "Súper (PC)", new BigDecimal("81"), HOY, null);
        telefono.avanzar(20); // el teléfono editó después
        telefono.gastos.editar(gasto.id(), "Súper (teléfono)", new BigDecimal("82"), HOY, null);

        sincronizar(pc, telefono, 0);

        assertEquals("Súper (teléfono)", pc.gastos.delMes(MES).getFirst().descripcion());
        assertEquals("Súper (teléfono)", telefono.gastos.delMes(MES).getFirst().descripcion());
    }

    @Test
    void unBorradoViajaYNoResucita() {
        Gasto gasto = pc.gastos.registrar("Súper", new BigDecimal("80"), HOY, null);
        sincronizar(pc, telefono, 0);

        telefono.avanzar(5);
        telefono.gastos.eliminar(gasto.id());
        sincronizar(pc, telefono, 0);
        assertTrue(pc.gastos.delMes(MES).isEmpty());

        // Volver a sincronizar lo mismo no lo revive ni cambia nada
        assertEquals(0, pc.cambios.aplicar(telefono.cambios.desde(0)));
        assertTrue(pc.gastos.delMes(MES).isEmpty());
    }

    @Test
    void soloViajaLoQueCambioDesdeLaUltimaVez() {
        pc.gastos.registrar("Viejo", new BigDecimal("1"), HOY, null);
        long marca = pc.ahora.toEpochMilli();
        pc.avanzar(30);
        pc.gastos.registrar("Nuevo", new BigDecimal("2"), HOY, null);

        JSONObject desdeMarca = pc.cambios.desde(marca);
        assertEquals(1, desdeMarca.getJSONArray("gastos").length());
        assertEquals("Nuevo", desdeMarca.getJSONArray("gastos").getJSONObject(0).getString("descripcion"));
        // Los ajustes propios del equipo (el token) nunca viajan; los demás sí
        pc.ajustes.guardar("sync_token", "secreto", pc.ahora);
        pc.ajustes.guardar("ingreso_mensual", "6500", pc.ahora);
        var claves = pc.cambios.desde(0).getJSONArray("ajustes").toList().stream()
                .map(a -> ((java.util.Map<?, ?>) a).get("clave")).toList();
        assertTrue(claves.contains("ingreso_mensual"));
        assertFalse(claves.contains("sync_token"));
        // Y al aplicar en el teléfono, el sueldo llega
        telefono.cambios.aplicar(pc.cambios.desde(0));
        assertEquals("6500", telefono.ajustes.leer("ingreso_mensual").orElseThrow());
    }
}
