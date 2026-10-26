package com.registrogastos.servicio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Una línea del historial de una cuenta, venga de donde venga (gasto, ingreso o transferencia).
 * Así la vista pinta una sola lista sin saber de tres tablas.
 *
 * @param monto con signo y en la moneda de la cuenta: negativo = salió dinero
 */
public record MovimientoCuenta(String id, Tipo tipo, String descripcion, BigDecimal monto, LocalDate fecha,
                               Instant creadoEn) {

    public enum Tipo { GASTO, INGRESO, ENTRADA, SALIDA }

    /** Los gastos se borran desde Movimientos; aquí solo ingresos y transferencias. */
    public boolean seQuitaAqui() {
        return tipo != Tipo.GASTO;
    }
}
