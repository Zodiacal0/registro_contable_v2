package com.registrogastos.persistencia;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * El "abrir conexión, preparar, poner parámetros, recorrer filas, cerrar" que se repetía en
 * cada repositorio, escrito una vez. Lo usan los repositorios de cuentas en adelante.
 *
 * {@link Mapeador} existe porque {@code java.util.function.Function} no puede lanzar
 * SQLException (una excepción verificada): hace falta una interfaz funcional propia que la
 * declare en su firma.
 */
final class Consultas {

    @FunctionalInterface
    interface Mapeador<T> {
        T mapear(ResultSet rs) throws SQLException;
    }

    private Consultas() {
    }

    static <T> List<T> listar(BaseDeDatos bd, String sql, Mapeador<T> mapeador, Object... parametros) {
        try (var conexion = bd.abrirConexion(); var ps = conexion.prepareStatement(sql)) {
            poner(ps, parametros);
            try (var rs = ps.executeQuery()) {
                var resultado = new ArrayList<T>();
                while (rs.next()) {
                    resultado.add(mapeador.mapear(rs));
                }
                return resultado;
            }
        } catch (SQLException e) {
            throw new ErrorDePersistencia("Falló la consulta: " + primeraLinea(sql), e);
        }
    }

    static <T> Optional<T> uno(BaseDeDatos bd, String sql, Mapeador<T> mapeador, Object... parametros) {
        return listar(bd, sql, mapeador, parametros).stream().findFirst();
    }

    /** INSERT/UPDATE; devuelve las filas afectadas. */
    static int ejecutar(BaseDeDatos bd, String sql, Object... parametros) {
        try (var conexion = bd.abrirConexion(); var ps = conexion.prepareStatement(sql)) {
            poner(ps, parametros);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErrorDePersistencia("Falló la escritura: " + primeraLinea(sql), e);
        }
    }

    /** setObject decide el tipo SQL por el tipo Java (String, Long, Integer; null -> NULL). */
    private static void poner(PreparedStatement ps, Object[] parametros) throws SQLException {
        for (int i = 0; i < parametros.length; i++) {
            ps.setObject(i + 1, parametros[i]);
        }
    }

    private static String primeraLinea(String sql) {
        return sql.strip().lines().findFirst().orElse("");
    }
}
