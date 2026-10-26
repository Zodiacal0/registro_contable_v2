package com.registrogastos.persistencia;

import com.registrogastos.dominio.Gasto;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Traduce entre objetos Gasto y filas de la tabla gastos.
 * Conversiones: BigDecimal <-> centavos (INTEGER), LocalDate <-> texto ISO,
 * Instant <-> milisegundos epoch.
 */
public final class GastoRepositorio {

    private final BaseDeDatos baseDeDatos;

    public GastoRepositorio(BaseDeDatos baseDeDatos) {
        this.baseDeDatos = baseDeDatos;
    }

    private static final String INSERTAR = """
            INSERT INTO gastos (id, descripcion, monto_centavos, fecha, categoria_id, recurrencia_id,
                                creado_en, actualizado_en, eliminado)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    /** Inserta o, si el id ya existe, actualiza ("upsert"). */
    public void guardar(Gasto gasto) {
        ejecutar(INSERTAR + """
                ON CONFLICT(id) DO UPDATE SET
                    descripcion = excluded.descripcion,
                    monto_centavos = excluded.monto_centavos,
                    fecha = excluded.fecha,
                    categoria_id = excluded.categoria_id,
                    actualizado_en = excluded.actualizado_en,
                    eliminado = excluded.eliminado
                """, gasto);
    }

    /**
     * Inserta solo si el id no existe. Para los cobros de recurrencias: si el usuario ya
     * editó o borró ese cobro (borrado lógico = la fila sigue ahí), no se pisa ni se revive.
     *
     * @return true si se insertó
     */
    public boolean insertarSiNoExiste(Gasto gasto) {
        return ejecutar(INSERTAR + "ON CONFLICT(id) DO NOTHING", gasto) > 0;
    }

    private int ejecutar(String sql, Gasto gasto) {
        try (var conexion = baseDeDatos.abrirConexion(); var ps = conexion.prepareStatement(sql)) {
            ps.setString(1, gasto.id());
            ps.setString(2, gasto.descripcion());
            ps.setLong(3, aCentavos(gasto.monto()));
            ps.setString(4, gasto.fecha().toString());
            ps.setString(5, gasto.categoriaId());   // null -> NULL en SQL
            ps.setString(6, gasto.recurrenciaId());
            ps.setLong(7, gasto.creadoEn().toEpochMilli());
            ps.setLong(8, gasto.actualizadoEn().toEpochMilli());
            ps.setInt(9, gasto.eliminado() ? 1 : 0);
            return ps.executeUpdate(); // filas afectadas: 0 si DO NOTHING no insertó
        } catch (SQLException e) {
            throw new ErrorDePersistencia("No se pudo guardar el gasto " + gasto.id(), e);
        }
    }

    /** Cobros no eliminados que generó una recurrencia, del más reciente al más antiguo. */
    public List<Gasto> listarPorRecurrencia(String recurrenciaId) {
        String sql = "SELECT * FROM gastos WHERE eliminado = 0 AND recurrencia_id = ? ORDER BY fecha DESC";
        try (var conexion = baseDeDatos.abrirConexion(); var ps = conexion.prepareStatement(sql)) {
            ps.setString(1, recurrenciaId);
            try (var rs = ps.executeQuery()) {
                var resultado = new ArrayList<Gasto>();
                while (rs.next()) {
                    resultado.add(mapear(rs));
                }
                return resultado;
            }
        } catch (SQLException e) {
            throw new ErrorDePersistencia("No se pudo listar los cobros de " + recurrenciaId, e);
        }
    }

    public Optional<Gasto> buscarPorId(String id) {
        String sql = "SELECT * FROM gastos WHERE id = ?";
        try (var conexion = baseDeDatos.abrirConexion(); var ps = conexion.prepareStatement(sql)) {
            ps.setString(1, id);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ErrorDePersistencia("No se pudo buscar el gasto " + id, e);
        }
    }

    /** Gastos no eliminados entre dos fechas (ambas incluidas), del más reciente al más antiguo. */
    public List<Gasto> listarEntre(LocalDate desde, LocalDate hasta) {
        // Comparar fechas como texto funciona porque el formato ISO ordena igual que la fecha
        String sql = """
                SELECT * FROM gastos
                WHERE eliminado = 0 AND fecha BETWEEN ? AND ?
                ORDER BY fecha DESC, creado_en DESC
                """;
        try (var conexion = baseDeDatos.abrirConexion(); var ps = conexion.prepareStatement(sql)) {
            ps.setString(1, desde.toString());
            ps.setString(2, hasta.toString());
            try (var rs = ps.executeQuery()) {
                var resultado = new ArrayList<Gasto>();
                while (rs.next()) {
                    resultado.add(mapear(rs));
                }
                return resultado;
            }
        } catch (SQLException e) {
            throw new ErrorDePersistencia("No se pudieron listar los gastos", e);
        }
    }

    private static Gasto mapear(ResultSet rs) throws SQLException {
        return new Gasto(
                rs.getString("id"),
                rs.getString("descripcion"),
                deCentavos(rs.getLong("monto_centavos")),
                LocalDate.parse(rs.getString("fecha")),
                rs.getString("categoria_id"),
                rs.getString("recurrencia_id"),
                Instant.ofEpochMilli(rs.getLong("creado_en")),
                Instant.ofEpochMilli(rs.getLong("actualizado_en")),
                rs.getInt("eliminado") == 1);
    }

    static long aCentavos(BigDecimal monto) {
        return monto.movePointRight(2).longValueExact(); // 12.34 -> 1234
    }

    static BigDecimal deCentavos(long centavos) {
        return BigDecimal.valueOf(centavos, 2); // 1234 -> 12.34
    }
}
