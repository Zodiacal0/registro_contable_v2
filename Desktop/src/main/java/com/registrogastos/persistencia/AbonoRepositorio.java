package com.registrogastos.persistencia;

import com.registrogastos.dominio.Abono;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.registrogastos.persistencia.GastoRepositorio.aCentavos;
import static com.registrogastos.persistencia.GastoRepositorio.deCentavos;

/**
 * Abonos de dos tablas con la misma forma: "aportes" (a metas) y "cobros_deuda" (de lo que me
 * deben). En vez de copiar la clase, se parametriza con la tabla y la columna del dueño; las
 * dos instancias se crean con las fábricas {@link #aportes} y {@link #cobrosDeuda}.
 */
public final class AbonoRepositorio {

    private final BaseDeDatos baseDeDatos;
    private final String tabla;
    private final String columnaDueno;

    private AbonoRepositorio(BaseDeDatos baseDeDatos, String tabla, String columnaDueno) {
        this.baseDeDatos = baseDeDatos;
        this.tabla = tabla;               // constantes de abajo: nunca texto del usuario,
        this.columnaDueno = columnaDueno; // por eso es seguro ponerlas dentro del SQL
    }

    public static AbonoRepositorio aportes(BaseDeDatos baseDeDatos) {
        return new AbonoRepositorio(baseDeDatos, "aportes", "meta_id");
    }

    public static AbonoRepositorio cobrosDeuda(BaseDeDatos baseDeDatos) {
        return new AbonoRepositorio(baseDeDatos, "cobros_deuda", "deuda_id");
    }

    public void guardar(Abono abono) {
        String sql = "INSERT INTO " + tabla + " (id, " + columnaDueno
                + ", monto_centavos, fecha, creado_en, actualizado_en, eliminado) VALUES (?, ?, ?, ?, ?, ?, ?)"
                + " ON CONFLICT(id) DO UPDATE SET monto_centavos = excluded.monto_centavos,"
                + " fecha = excluded.fecha, actualizado_en = excluded.actualizado_en, eliminado = excluded.eliminado";
        try (var conexion = baseDeDatos.abrirConexion(); var ps = conexion.prepareStatement(sql)) {
            ps.setString(1, abono.id());
            ps.setString(2, abono.duenoId());
            ps.setLong(3, aCentavos(abono.monto()));
            ps.setString(4, abono.fecha().toString());
            ps.setLong(5, abono.creadoEn().toEpochMilli());
            ps.setLong(6, abono.actualizadoEn().toEpochMilli());
            ps.setInt(7, abono.eliminado() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErrorDePersistencia("No se pudo guardar en " + tabla, e);
        }
    }

    public Optional<Abono> buscarPorId(String id) {
        String sql = "SELECT * FROM " + tabla + " WHERE id = ?";
        try (var conexion = baseDeDatos.abrirConexion(); var ps = conexion.prepareStatement(sql)) {
            ps.setString(1, id);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ErrorDePersistencia("No se pudo buscar en " + tabla, e);
        }
    }

    /** Los no eliminados de un dueño (meta o deuda), del más reciente al más antiguo. */
    public List<Abono> listarDe(String duenoId) {
        String sql = "SELECT * FROM " + tabla + " WHERE eliminado = 0 AND " + columnaDueno
                + " = ? ORDER BY fecha DESC, creado_en DESC";
        try (var conexion = baseDeDatos.abrirConexion(); var ps = conexion.prepareStatement(sql)) {
            ps.setString(1, duenoId);
            try (var rs = ps.executeQuery()) {
                var resultado = new ArrayList<Abono>();
                while (rs.next()) {
                    resultado.add(mapear(rs));
                }
                return resultado;
            }
        } catch (SQLException e) {
            throw new ErrorDePersistencia("No se pudo listar " + tabla, e);
        }
    }

    private Abono mapear(ResultSet rs) throws SQLException {
        return new Abono(
                rs.getString("id"),
                rs.getString(columnaDueno),
                deCentavos(rs.getLong("monto_centavos")),
                LocalDate.parse(rs.getString("fecha")),
                Instant.ofEpochMilli(rs.getLong("creado_en")),
                Instant.ofEpochMilli(rs.getLong("actualizado_en")),
                rs.getInt("eliminado") == 1);
    }
}
