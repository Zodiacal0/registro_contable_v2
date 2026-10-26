package com.registrogastos.persistencia;

import com.registrogastos.dominio.Recurrencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.registrogastos.persistencia.GastoRepositorio.aCentavos;
import static com.registrogastos.persistencia.GastoRepositorio.deCentavos;

/** Traduce entre objetos Recurrencia y filas de la tabla recurrencias. */
public final class RecurrenciaRepositorio {

    private final BaseDeDatos baseDeDatos;

    public RecurrenciaRepositorio(BaseDeDatos baseDeDatos) {
        this.baseDeDatos = baseDeDatos;
    }

    public void guardar(Recurrencia r) {
        String sql = """
                INSERT INTO recurrencias (id, descripcion, monto_centavos, categoria_id, cada, unidad,
                                          inicio, generada_hasta, activa, actualizado_en, eliminado)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    descripcion = excluded.descripcion,
                    monto_centavos = excluded.monto_centavos,
                    categoria_id = excluded.categoria_id,
                    cada = excluded.cada,
                    unidad = excluded.unidad,
                    generada_hasta = excluded.generada_hasta,
                    activa = excluded.activa,
                    actualizado_en = excluded.actualizado_en,
                    eliminado = excluded.eliminado
                """;
        try (var conexion = baseDeDatos.abrirConexion(); var ps = conexion.prepareStatement(sql)) {
            ps.setString(1, r.id());
            ps.setString(2, r.descripcion());
            ps.setLong(3, aCentavos(r.monto()));
            ps.setString(4, r.categoriaId());
            ps.setInt(5, r.cada());
            ps.setString(6, r.unidad().name()); // el enum se guarda por nombre: "MESES"
            ps.setString(7, r.inicio().toString());
            ps.setString(8, r.generadaHasta() == null ? null : r.generadaHasta().toString());
            ps.setInt(9, r.activa() ? 1 : 0);
            ps.setLong(10, r.actualizadoEn().toEpochMilli());
            ps.setInt(11, r.eliminado() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErrorDePersistencia("No se pudo guardar la recurrencia " + r.id(), e);
        }
    }

    public Optional<Recurrencia> buscarPorId(String id) {
        String sql = "SELECT * FROM recurrencias WHERE id = ?";
        try (var conexion = baseDeDatos.abrirConexion(); var ps = conexion.prepareStatement(sql)) {
            ps.setString(1, id);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ErrorDePersistencia("No se pudo buscar la recurrencia " + id, e);
        }
    }

    /** Las no eliminadas (activas y en pausa), ordenadas por nombre. */
    public List<Recurrencia> listar() {
        String sql = "SELECT * FROM recurrencias WHERE eliminado = 0 ORDER BY descripcion COLLATE NOCASE";
        try (var conexion = baseDeDatos.abrirConexion();
             var ps = conexion.prepareStatement(sql);
             var rs = ps.executeQuery()) {
            var resultado = new ArrayList<Recurrencia>();
            while (rs.next()) {
                resultado.add(mapear(rs));
            }
            return resultado;
        } catch (SQLException e) {
            throw new ErrorDePersistencia("No se pudieron listar las recurrencias", e);
        }
    }

    private static Recurrencia mapear(ResultSet rs) throws SQLException {
        String generada = rs.getString("generada_hasta");
        return new Recurrencia(
                rs.getString("id"),
                rs.getString("descripcion"),
                deCentavos(rs.getLong("monto_centavos")),
                rs.getString("categoria_id"),
                rs.getInt("cada"),
                Recurrencia.Unidad.valueOf(rs.getString("unidad")),
                LocalDate.parse(rs.getString("inicio")),
                generada == null ? null : LocalDate.parse(generada),
                rs.getInt("activa") == 1,
                Instant.ofEpochMilli(rs.getLong("actualizado_en")),
                rs.getInt("eliminado") == 1);
    }
}
