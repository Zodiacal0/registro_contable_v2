package com.registrogastos.persistencia;

import com.registrogastos.dominio.LineaPlan;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.registrogastos.persistencia.GastoRepositorio.aCentavos;
import static com.registrogastos.persistencia.GastoRepositorio.deCentavos;

/** Traduce entre objetos LineaPlan y filas de la tabla plan_lineas. */
public final class LineaPlanRepositorio {

    private final BaseDeDatos bd;

    public LineaPlanRepositorio(BaseDeDatos bd) {
        this.bd = bd;
    }

    public void guardar(LineaPlan l) {
        Consultas.ejecutar(bd, """
                INSERT INTO plan_lineas (id, nombre, monto_centavos, grupo, orden, actualizado_en, eliminado, quincena)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    nombre = excluded.nombre,
                    monto_centavos = excluded.monto_centavos,
                    quincena = excluded.quincena,
                    actualizado_en = excluded.actualizado_en,
                    eliminado = excluded.eliminado
                """, l.id(), l.nombre(), aCentavos(l.monto()), l.grupo().name(), l.orden(),
                l.actualizadoEn().toEpochMilli(), l.eliminado() ? 1 : 0, l.quincena().name());
    }

    public Optional<LineaPlan> buscarPorId(String id) {
        return Consultas.uno(bd, "SELECT * FROM plan_lineas WHERE id = ?", LineaPlanRepositorio::mapear, id);
    }

    public List<LineaPlan> listarActivas() {
        return Consultas.listar(bd, "SELECT * FROM plan_lineas WHERE eliminado = 0 ORDER BY orden, actualizado_en",
                LineaPlanRepositorio::mapear);
    }

    /** El mayor orden usado (incluidas eliminadas, para que una nueva siempre vaya al final). */
    public int ultimoOrden() {
        return Consultas.uno(bd, "SELECT COALESCE(MAX(orden), 0) AS o FROM plan_lineas", rs -> rs.getInt("o")).orElse(0);
    }

    private static LineaPlan mapear(ResultSet rs) throws SQLException {
        return new LineaPlan(
                rs.getString("id"),
                rs.getString("nombre"),
                deCentavos(rs.getLong("monto_centavos")),
                LineaPlan.Grupo.valueOf(rs.getString("grupo")),
                LineaPlan.Quincena.valueOf(rs.getString("quincena")),
                rs.getInt("orden"),
                Instant.ofEpochMilli(rs.getLong("actualizado_en")),
                rs.getInt("eliminado") == 1);
    }
}
