package com.registrogastos.persistencia;

import com.registrogastos.dominio.Meta;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.registrogastos.persistencia.GastoRepositorio.aCentavos;
import static com.registrogastos.persistencia.GastoRepositorio.deCentavos;

/** Traduce entre objetos Meta y filas de la tabla metas. */
public final class MetaRepositorio {

    private final BaseDeDatos baseDeDatos;

    public MetaRepositorio(BaseDeDatos baseDeDatos) {
        this.baseDeDatos = baseDeDatos;
    }

    public void guardar(Meta meta) {
        String sql = """
                INSERT INTO metas (id, nombre, objetivo_centavos, fecha_objetivo, color, actualizado_en, eliminado)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    nombre = excluded.nombre,
                    objetivo_centavos = excluded.objetivo_centavos,
                    fecha_objetivo = excluded.fecha_objetivo,
                    color = excluded.color,
                    actualizado_en = excluded.actualizado_en,
                    eliminado = excluded.eliminado
                """;
        try (var conexion = baseDeDatos.abrirConexion(); var ps = conexion.prepareStatement(sql)) {
            ps.setString(1, meta.id());
            ps.setString(2, meta.nombre());
            ps.setLong(3, aCentavos(meta.objetivo()));
            ps.setString(4, meta.fechaObjetivo() == null ? null : meta.fechaObjetivo().toString());
            ps.setString(5, meta.color());
            ps.setLong(6, meta.actualizadoEn().toEpochMilli());
            ps.setInt(7, meta.eliminado() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErrorDePersistencia("No se pudo guardar la meta " + meta.id(), e);
        }
    }

    public Optional<Meta> buscarPorId(String id) {
        String sql = "SELECT * FROM metas WHERE id = ?";
        try (var conexion = baseDeDatos.abrirConexion(); var ps = conexion.prepareStatement(sql)) {
            ps.setString(1, id);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ErrorDePersistencia("No se pudo buscar la meta " + id, e);
        }
    }

    public List<Meta> listarActivas() {
        String sql = "SELECT * FROM metas WHERE eliminado = 0 ORDER BY nombre COLLATE NOCASE";
        try (var conexion = baseDeDatos.abrirConexion();
             var ps = conexion.prepareStatement(sql);
             var rs = ps.executeQuery()) {
            var resultado = new ArrayList<Meta>();
            while (rs.next()) {
                resultado.add(mapear(rs));
            }
            return resultado;
        } catch (SQLException e) {
            throw new ErrorDePersistencia("No se pudieron listar las metas", e);
        }
    }

    private static Meta mapear(ResultSet rs) throws SQLException {
        String fecha = rs.getString("fecha_objetivo");
        return new Meta(
                rs.getString("id"),
                rs.getString("nombre"),
                deCentavos(rs.getLong("objetivo_centavos")),
                fecha == null ? null : LocalDate.parse(fecha),
                rs.getString("color"),
                Instant.ofEpochMilli(rs.getLong("actualizado_en")),
                rs.getInt("eliminado") == 1);
    }
}
