package com.registrogastos.persistencia;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Lleva el esquema de la base a la última versión.
 *
 * SQLite guarda un entero libre en la cabecera del archivo: PRAGMA user_version.
 * Lo usamos como "número de la última migración aplicada". Al arrancar, se
 * ejecutan solo las migraciones con número mayor.
 */
public final class Migrador {

    /** En orden. La posición + 1 es el número de versión. Solo se agregan al final. */
    private static final List<String> MIGRACIONES = List.of(
            "V1__esquema_inicial.sql",
            "V2__color_categoria.sql",
            "V3__paleta_violeta.sql",
            "V4__categorias_en_tono.sql",
            "V5__recurrencias.sql"
    );

    private final BaseDeDatos baseDeDatos;

    public Migrador(BaseDeDatos baseDeDatos) {
        this.baseDeDatos = baseDeDatos;
    }

    public void migrar() {
        try (Connection conexion = baseDeDatos.abrirConexion()) {
            int actual = versionActual(conexion);
            for (int version = actual + 1; version <= MIGRACIONES.size(); version++) {
                aplicar(conexion, version, MIGRACIONES.get(version - 1));
            }
        } catch (SQLException e) {
            throw new ErrorDePersistencia("Falló la migración de la base de datos", e);
        }
    }

    public int versionActual() {
        try (Connection conexion = baseDeDatos.abrirConexion()) {
            return versionActual(conexion);
        } catch (SQLException e) {
            throw new ErrorDePersistencia("No se pudo leer la versión del esquema", e);
        }
    }

    private static int versionActual(Connection conexion) throws SQLException {
        try (var st = conexion.createStatement(); var rs = st.executeQuery("PRAGMA user_version")) {
            return rs.getInt(1);
        }
    }

    /** Todo o nada: si una sentencia falla, la migración entera se deshace. */
    private static void aplicar(Connection conexion, int version, String archivo) throws SQLException {
        conexion.setAutoCommit(false);
        try (var st = conexion.createStatement()) {
            for (String sentencia : sentencias(leerRecurso(archivo))) {
                st.executeUpdate(sentencia);
            }
            st.executeUpdate("PRAGMA user_version = " + version);
            conexion.commit();
        } catch (SQLException e) {
            conexion.rollback();
            throw e;
        } finally {
            conexion.setAutoCommit(true);
        }
    }

    /** Quita los comentarios "--" y separa por ";". Suficiente para nuestros .sql simples. */
    private static List<String> sentencias(String sql) {
        String sinComentarios = sql.lines()
                .map(linea -> linea.replaceFirst("--.*$", ""))
                .collect(Collectors.joining("\n"));
        return Arrays.stream(sinComentarios.split(";"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String leerRecurso(String archivo) {
        String ruta = "/db/migraciones/" + archivo;
        try (InputStream in = Migrador.class.getResourceAsStream(ruta)) {
            if (in == null) {
                throw new IllegalStateException("No existe el recurso " + ruta);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + ruta, e);
        }
    }
}
