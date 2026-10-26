package com.registrogastos.persistencia;

import com.registrogastos.dominio.Cuenta;
import com.registrogastos.dominio.Moneda;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.registrogastos.persistencia.GastoRepositorio.aCentavos;
import static com.registrogastos.persistencia.GastoRepositorio.deCentavos;

/** Traduce entre objetos Cuenta y filas de la tabla cuentas. */
public final class CuentaRepositorio {

    private final BaseDeDatos bd;

    public CuentaRepositorio(BaseDeDatos bd) {
        this.bd = bd;
    }

    /** La moneda no está en el UPDATE: no se puede cambiar después de crear la cuenta. */
    public void guardar(Cuenta c) {
        Consultas.ejecutar(bd, """
                INSERT INTO cuentas (id, nombre, tipo, moneda, saldo_inicial_centavos, actualizado_en, eliminado)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    nombre = excluded.nombre,
                    tipo = excluded.tipo,
                    saldo_inicial_centavos = excluded.saldo_inicial_centavos,
                    actualizado_en = excluded.actualizado_en,
                    eliminado = excluded.eliminado
                """, c.id(), c.nombre(), c.tipo().name(), c.moneda().name(), aCentavos(c.saldoInicial()),
                c.actualizadoEn().toEpochMilli(), c.eliminado() ? 1 : 0);
    }

    public Optional<Cuenta> buscarPorId(String id) {
        return Consultas.uno(bd, "SELECT * FROM cuentas WHERE id = ?", CuentaRepositorio::mapear, id);
    }

    /** La principal primero; después, por nombre. */
    public List<Cuenta> listarActivas() {
        return Consultas.listar(bd, """
                SELECT * FROM cuentas WHERE eliminado = 0
                ORDER BY id = ? DESC, nombre COLLATE NOCASE
                """, CuentaRepositorio::mapear, Cuenta.PRINCIPAL_ID);
    }

    /** Todas, incluidas las eliminadas: sus gastos siguen existiendo y hay que saber su moneda. */
    public List<Cuenta> listarTodas() {
        return Consultas.listar(bd, "SELECT * FROM cuentas", CuentaRepositorio::mapear);
    }

    private static Cuenta mapear(ResultSet rs) throws SQLException {
        return new Cuenta(
                rs.getString("id"),
                rs.getString("nombre"),
                Cuenta.Tipo.valueOf(rs.getString("tipo")),
                Moneda.valueOf(rs.getString("moneda")),
                deCentavos(rs.getLong("saldo_inicial_centavos")),
                Instant.ofEpochMilli(rs.getLong("actualizado_en")),
                rs.getInt("eliminado") == 1);
    }
}
