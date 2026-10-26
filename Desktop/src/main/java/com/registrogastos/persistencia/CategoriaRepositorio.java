package com.registrogastos.persistencia;

import com.registrogastos.dominio.Categoria;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Traduce entre objetos Categoria y filas de la tabla categorias. */
public final class CategoriaRepositorio {

    private final BaseDeDatos baseDeDatos;

    public CategoriaRepositorio(BaseDeDatos baseDeDatos) {
        this.baseDeDatos = baseDeDatos;
    }

    /** Inserta si el id no existe; si existe, lo actualiza ("upsert"). */
    public void guardar(Categoria categoria) {
        String sql = """
                INSERT INTO categorias (id, nombre, color, actualizado_en, eliminado)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    nombre = excluded.nombre,
                    color = excluded.color,
                    actualizado_en = excluded.actualizado_en,
                    eliminado = excluded.eliminado
                """;
        try (var conexion = baseDeDatos.abrirConexion(); var ps = conexion.prepareStatement(sql)) {
            ps.setString(1, categoria.id());
            ps.setString(2, categoria.nombre());
            ps.setString(3, categoria.color());
            ps.setLong(4, categoria.actualizadoEn().toEpochMilli());
            ps.setInt(5, categoria.eliminado() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErrorDePersistencia("No se pudo guardar la categoría " + categoria.id(), e);
        }
    }

    public Optional<Categoria> buscarPorId(String id) {
        String sql = "SELECT * FROM categorias WHERE id = ?";
        try (var conexion = baseDeDatos.abrirConexion(); var ps = conexion.prepareStatement(sql)) {
            ps.setString(1, id);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ErrorDePersistencia("No se pudo buscar la categoría " + id, e);
        }
    }

    public List<Categoria> listarActivas() {
        String sql = "SELECT * FROM categorias WHERE eliminado = 0 ORDER BY nombre COLLATE NOCASE";
        try (var conexion = baseDeDatos.abrirConexion();
             var ps = conexion.prepareStatement(sql);
             var rs = ps.executeQuery()) {
            var resultado = new ArrayList<Categoria>();
            while (rs.next()) {
                resultado.add(mapear(rs));
            }
            return resultado;
        } catch (SQLException e) {
            throw new ErrorDePersistencia("No se pudieron listar las categorías", e);
        }
    }

    private static Categoria mapear(ResultSet rs) throws SQLException {
        return new Categoria(
                rs.getString("id"),
                rs.getString("nombre"),
                rs.getString("color"),
                Instant.ofEpochMilli(rs.getLong("actualizado_en")),
                rs.getInt("eliminado") == 1);
    }
}
