package com.registrogastos.persistencia;

import com.registrogastos.dominio.Ingreso;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.registrogastos.persistencia.GastoRepositorio.aCentavos;
import static com.registrogastos.persistencia.GastoRepositorio.deCentavos;

/** Traduce entre objetos Ingreso y filas de la tabla ingresos. */
public final class IngresoRepositorio {

    private final BaseDeDatos bd;

    public IngresoRepositorio(BaseDeDatos bd) {
        this.bd = bd;
    }

    public void guardar(Ingreso i) {
        Consultas.ejecutar(bd, """
                INSERT INTO ingresos (id, cuenta_id, descripcion, monto_centavos, fecha, creado_en, actualizado_en, eliminado)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    descripcion = excluded.descripcion,
                    monto_centavos = excluded.monto_centavos,
                    fecha = excluded.fecha,
                    actualizado_en = excluded.actualizado_en,
                    eliminado = excluded.eliminado
                """, i.id(), i.cuentaId(), i.descripcion(), aCentavos(i.monto()), i.fecha().toString(),
                i.creadoEn().toEpochMilli(), i.actualizadoEn().toEpochMilli(), i.eliminado() ? 1 : 0);
    }

    public Optional<Ingreso> buscarPorId(String id) {
        return Consultas.uno(bd, "SELECT * FROM ingresos WHERE id = ?", IngresoRepositorio::mapear, id);
    }

    public List<Ingreso> listarDe(String cuentaId) {
        return Consultas.listar(bd, """
                SELECT * FROM ingresos WHERE eliminado = 0 AND cuenta_id = ?
                ORDER BY fecha DESC, creado_en DESC
                """, IngresoRepositorio::mapear, cuentaId);
    }

    /** Ingresos no eliminados entre dos fechas (ambas incluidas), de todas las cuentas. */
    public List<Ingreso> listarEntre(LocalDate desde, LocalDate hasta) {
        return Consultas.listar(bd, """
                SELECT * FROM ingresos WHERE eliminado = 0 AND fecha BETWEEN ? AND ?
                ORDER BY fecha DESC, creado_en DESC
                """, IngresoRepositorio::mapear, desde.toString(), hasta.toString());
    }

    private static Ingreso mapear(ResultSet rs) throws SQLException {
        return new Ingreso(
                rs.getString("id"),
                rs.getString("cuenta_id"),
                rs.getString("descripcion"),
                deCentavos(rs.getLong("monto_centavos")),
                LocalDate.parse(rs.getString("fecha")),
                Instant.ofEpochMilli(rs.getLong("creado_en")),
                Instant.ofEpochMilli(rs.getLong("actualizado_en")),
                rs.getInt("eliminado") == 1);
    }
}
