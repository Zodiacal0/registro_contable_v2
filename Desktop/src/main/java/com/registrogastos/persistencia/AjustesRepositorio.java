package com.registrogastos.persistencia;

import java.time.Instant;
import java.util.Optional;

/** Ajustes clave/valor (texto). Por ahora: el tipo de cambio de cada moneda. */
public final class AjustesRepositorio {

    private final BaseDeDatos bd;

    public AjustesRepositorio(BaseDeDatos bd) {
        this.bd = bd;
    }

    public Optional<String> leer(String clave) {
        return Consultas.uno(bd, "SELECT valor FROM ajustes WHERE clave = ?", rs -> rs.getString("valor"), clave);
    }

    public void guardar(String clave, String valor, Instant ahora) {
        Consultas.ejecutar(bd, """
                INSERT INTO ajustes (clave, valor, actualizado_en) VALUES (?, ?, ?)
                ON CONFLICT(clave) DO UPDATE SET valor = excluded.valor, actualizado_en = excluded.actualizado_en
                """, clave, valor, ahora.toEpochMilli());
    }
}
