package com.registrogastos.sincronizacion;

import com.registrogastos.persistencia.BaseDeDatos;
import com.registrogastos.persistencia.ErrorDePersistencia;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * El "feed de cambios" de la base: qué filas cambiaron desde un momento, y cómo aplicar las
 * que llegan de otro dispositivo. Es genérico: trabaja con cualquier tabla que tenga
 * {@code actualizado_en}, leyendo sus columnas con {@code PRAGMA table_info}. Así, una tabla
 * nueva solo hay que agregarla a {@link #TABLAS} para que sincronice.
 *
 * Resolución de conflictos: <b>gana la última escritura</b> (last-writer-wins). Al aplicar
 * una fila remota, el UPSERT solo pisa la local si la remota es más nueva
 * ({@code excluded.actualizado_en > actualizado_en}). Los borrados son lógicos (columna
 * {@code eliminado}), así que viajan como una fila más y nunca "resucitan" por un UPSERT.
 *
 * Formato (JSON): {@code {"tabla": [{columna: valor, ...}, ...], ...}}. Los enteros van como
 * número y el resto como texto; NULL como null.
 */
public final class Cambios {

    /**
     * En orden de claves foráneas: primero las tablas de las que dependen las demás. Las
     * foráneas están activas en cada conexión, así que insertar un gasto antes que su
     * categoría fallaría. El segundo valor es la columna clave.
     */
    static final List<Map.Entry<String, String>> TABLAS = List.of(
            Map.entry("categorias", "id"),
            Map.entry("cuentas", "id"),
            Map.entry("recurrencias", "id"),
            Map.entry("metas", "id"),
            Map.entry("deudas", "id"),
            Map.entry("gastos", "id"),
            Map.entry("aportes", "id"),
            Map.entry("cobros_deuda", "id"),
            Map.entry("ingresos", "id"),
            Map.entry("transferencias", "id"),
            Map.entry("plan_lineas", "id"),
            Map.entry("ajustes", "clave"));

    /** Ajustes que son de ESTE equipo y no deben viajar (el token de emparejamiento, etc.). */
    static final List<String> AJUSTES_LOCALES = List.of(
            "sync_token", "sync_ultima",                     // escritorio
            "sync_host", "sync_enviado", "sync_recibido");   // teléfono (src/sync/cambios.ts)

    private final BaseDeDatos bd;
    private final Map<String, List<String>> columnas = new LinkedHashMap<>();

    public Cambios(BaseDeDatos bd) {
        this.bd = bd;
    }

    /** Todo lo que cambió DESPUÉS de {@code desde} (epoch ms), por tabla. */
    public JSONObject desde(long desde) {
        var resultado = new JSONObject();
        try (var conexion = bd.abrirConexion()) {
            for (var tabla : TABLAS) {
                String nombre = tabla.getKey();
                var filas = new JSONArray();
                String sql = "SELECT * FROM " + nombre + " WHERE actualizado_en > ?";
                try (var ps = conexion.prepareStatement(sql)) {
                    ps.setLong(1, desde);
                    try (var rs = ps.executeQuery()) {
                        var meta = rs.getMetaData();
                        while (rs.next()) {
                            var fila = new JSONObject();
                            for (int i = 1; i <= meta.getColumnCount(); i++) {
                                Object valor = rs.getObject(i);
                                fila.put(meta.getColumnName(i), valor == null ? JSONObject.NULL : valor);
                            }
                            if (nombre.equals("ajustes") && AJUSTES_LOCALES.contains(fila.getString("clave"))) {
                                continue;
                            }
                            filas.put(fila);
                        }
                    }
                }
                if (!filas.isEmpty()) {
                    resultado.put(nombre, filas);
                }
            }
        } catch (SQLException e) {
            throw new ErrorDePersistencia("No se pudieron leer los cambios", e);
        }
        return resultado;
    }

    /**
     * Aplica cambios de otro dispositivo en una sola transacción: o entran todos o ninguno.
     *
     * @return cuántas filas cambiaron de verdad (las que perdieron por ser más viejas no cuentan)
     */
    public int aplicar(JSONObject cambios) {
        int aplicadas = 0;
        try (var conexion = bd.abrirConexion()) {
            conexion.setAutoCommit(false);
            try {
                for (var tabla : TABLAS) {
                    String nombre = tabla.getKey();
                    if (!cambios.has(nombre)) {
                        continue;
                    }
                    JSONArray filas = cambios.getJSONArray(nombre);
                    List<String> cols = columnasDe(conexion, nombre);
                    try (var ps = conexion.prepareStatement(upsert(nombre, tabla.getValue(), cols))) {
                        for (int i = 0; i < filas.length(); i++) {
                            JSONObject fila = filas.getJSONObject(i);
                            if (nombre.equals("ajustes") && AJUSTES_LOCALES.contains(fila.optString("clave"))) {
                                continue;
                            }
                            for (int c = 0; c < cols.size(); c++) {
                                Object valor = fila.opt(cols.get(c));
                                ps.setObject(c + 1, valor == null || JSONObject.NULL.equals(valor) ? null : valor);
                            }
                            aplicadas += ps.executeUpdate();
                        }
                    }
                }
                conexion.commit();
            } catch (SQLException | RuntimeException e) {
                conexion.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new ErrorDePersistencia("No se pudieron aplicar los cambios", e);
        }
        return aplicadas;
    }

    /** INSERT ... ON CONFLICT(clave) DO UPDATE SET col = excluded.col ... WHERE la remota es más nueva. */
    private static String upsert(String tabla, String clave, List<String> cols) {
        var sets = new ArrayList<String>();
        for (String col : cols) {
            if (!col.equals(clave)) {
                sets.add(col + " = excluded." + col);
            }
        }
        return "INSERT INTO " + tabla + " (" + String.join(", ", cols) + ") VALUES ("
                + "?, ".repeat(cols.size() - 1) + "?) ON CONFLICT(" + clave + ") DO UPDATE SET "
                + String.join(", ", sets) + " WHERE excluded.actualizado_en > " + tabla + ".actualizado_en";
    }

    private List<String> columnasDe(Connection conexion, String tabla) throws SQLException {
        if (columnas.containsKey(tabla)) {
            return columnas.get(tabla);
        }
        var lista = new ArrayList<String>();
        try (var st = conexion.createStatement(); var rs = st.executeQuery("PRAGMA table_info(" + tabla + ")")) {
            while (rs.next()) {
                lista.add(rs.getString("name"));
            }
        }
        columnas.put(tabla, List.copyOf(lista));
        return columnas.get(tabla);
    }

    /** Para pruebas y diagnósticos: la versión del esquema, que ambos lados deben compartir. */
    public int versionEsquema() {
        try (var conexion = bd.abrirConexion(); var st = conexion.createStatement();
             var rs = st.executeQuery("PRAGMA user_version")) {
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new ErrorDePersistencia("No se pudo leer la versión", e);
        }
    }
}
