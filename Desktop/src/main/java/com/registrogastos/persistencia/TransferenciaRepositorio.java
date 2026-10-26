package com.registrogastos.persistencia;

import com.registrogastos.dominio.Transferencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.registrogastos.persistencia.GastoRepositorio.aCentavos;
import static com.registrogastos.persistencia.GastoRepositorio.deCentavos;

/** Traduce entre objetos Transferencia y filas de la tabla transferencias. */
public final class TransferenciaRepositorio {

    private final BaseDeDatos bd;

    public TransferenciaRepositorio(BaseDeDatos bd) {
        this.bd = bd;
    }

    /** Una transferencia no se edita (se borra y se hace otra): el UPDATE solo toca el borrado. */
    public void guardar(Transferencia t) {
        Consultas.ejecutar(bd, """
                INSERT INTO transferencias (id, origen_id, destino_id, salida_centavos, entrada_centavos, fecha,
                                            creado_en, actualizado_en, eliminado)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    actualizado_en = excluded.actualizado_en,
                    eliminado = excluded.eliminado
                """, t.id(), t.origenId(), t.destinoId(), aCentavos(t.salida()), aCentavos(t.entrada()),
                t.fecha().toString(), t.creadoEn().toEpochMilli(), t.actualizadoEn().toEpochMilli(),
                t.eliminado() ? 1 : 0);
    }

    public Optional<Transferencia> buscarPorId(String id) {
        return Consultas.uno(bd, "SELECT * FROM transferencias WHERE id = ?", TransferenciaRepositorio::mapear, id);
    }

    /** Las que salen de la cuenta o entran a ella. */
    public List<Transferencia> listarDe(String cuentaId) {
        return Consultas.listar(bd, """
                SELECT * FROM transferencias WHERE eliminado = 0 AND (origen_id = ? OR destino_id = ?)
                ORDER BY fecha DESC, creado_en DESC
                """, TransferenciaRepositorio::mapear, cuentaId, cuentaId);
    }

    private static Transferencia mapear(ResultSet rs) throws SQLException {
        return new Transferencia(
                rs.getString("id"),
                rs.getString("origen_id"),
                rs.getString("destino_id"),
                deCentavos(rs.getLong("salida_centavos")),
                deCentavos(rs.getLong("entrada_centavos")),
                LocalDate.parse(rs.getString("fecha")),
                Instant.ofEpochMilli(rs.getLong("creado_en")),
                Instant.ofEpochMilli(rs.getLong("actualizado_en")),
                rs.getInt("eliminado") == 1);
    }
}
