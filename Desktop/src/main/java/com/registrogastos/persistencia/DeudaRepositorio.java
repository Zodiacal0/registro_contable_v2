package com.registrogastos.persistencia;

import com.registrogastos.dominio.Deuda;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.registrogastos.persistencia.GastoRepositorio.aCentavos;
import static com.registrogastos.persistencia.GastoRepositorio.deCentavos;

/** Traduce entre objetos Deuda y filas de la tabla deudas. */
public final class DeudaRepositorio {

    private final BaseDeDatos baseDeDatos;

    public DeudaRepositorio(BaseDeDatos baseDeDatos) {
        this.baseDeDatos = baseDeDatos;
    }

    public void guardar(Deuda deuda) {
        String sql = """
                INSERT INTO deudas (id, nombre, tipo, monto_centavos, fecha, fecha_limite, actualizado_en, eliminado)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    nombre = excluded.nombre,
                    monto_centavos = excluded.monto_centavos,
                    fecha_limite = excluded.fecha_limite,
                    actualizado_en = excluded.actualizado_en,
                    eliminado = excluded.eliminado
                """;
        try (var conexion = baseDeDatos.abrirConexion(); var ps = conexion.prepareStatement(sql)) {
            ps.setString(1, deuda.id());
            ps.setString(2, deuda.nombre());
            ps.setString(3, deuda.tipo().name());
            ps.setLong(4, aCentavos(deuda.monto()));
            ps.setString(5, deuda.fecha().toString());
            ps.setString(6, deuda.fechaLimite() == null ? null : deuda.fechaLimite().toString());
            ps.setLong(7, deuda.actualizadoEn().toEpochMilli());
            ps.setInt(8, deuda.eliminado() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ErrorDePersistencia("No se pudo guardar la deuda " + deuda.id(), e);
        }
    }

    public Optional<Deuda> buscarPorId(String id) {
        String sql = "SELECT * FROM deudas WHERE id = ?";
        try (var conexion = baseDeDatos.abrirConexion(); var ps = conexion.prepareStatement(sql)) {
            ps.setString(1, id);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ErrorDePersistencia("No se pudo buscar la deuda " + id, e);
        }
    }

    public List<Deuda> listarActivas() {
        String sql = "SELECT * FROM deudas WHERE eliminado = 0 ORDER BY fecha DESC";
        try (var conexion = baseDeDatos.abrirConexion();
             var ps = conexion.prepareStatement(sql);
             var rs = ps.executeQuery()) {
            var resultado = new ArrayList<Deuda>();
            while (rs.next()) {
                resultado.add(mapear(rs));
            }
            return resultado;
        } catch (SQLException e) {
            throw new ErrorDePersistencia("No se pudieron listar las deudas", e);
        }
    }

    private static Deuda mapear(ResultSet rs) throws SQLException {
        String limite = rs.getString("fecha_limite");
        return new Deuda(
                rs.getString("id"),
                rs.getString("nombre"),
                Deuda.Tipo.valueOf(rs.getString("tipo")),
                deCentavos(rs.getLong("monto_centavos")),
                LocalDate.parse(rs.getString("fecha")),
                limite == null ? null : LocalDate.parse(limite),
                Instant.ofEpochMilli(rs.getLong("actualizado_en")),
                rs.getInt("eliminado") == 1);
    }
}
