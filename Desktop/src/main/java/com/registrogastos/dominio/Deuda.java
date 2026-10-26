package com.registrogastos.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Una deuda: lo que debo (tarjeta, préstamo, a un amigo) o lo que me deben.
 *
 * @param monto       el total original; lo pendiente se calcula restando los abonos
 * @param fechaLimite opcional: para cuándo debería quedar saldada
 */
public record Deuda(
        String id,
        String nombre,
        Tipo tipo,
        BigDecimal monto,
        LocalDate fecha,
        LocalDate fechaLimite,
        Instant actualizadoEn,
        boolean eliminado) {

    public enum Tipo {
        /** Yo debo: cada abono es un gasto. */
        DEBO,
        /** Me deben: lo que me pagan baja el saldo, pero no es un gasto. */
        ME_DEBEN
    }

    public Deuda {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(fecha, "fecha");
        Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        nombre = Montos.textoObligatorio(nombre, "El nombre de la deuda no puede estar vacío");
        monto = Montos.positivo(monto);
        if (fechaLimite != null && fechaLimite.isBefore(fecha)) {
            throw new IllegalArgumentException("La fecha límite no puede ser anterior al inicio");
        }
    }

    public static Deuda nueva(String nombre, Tipo tipo, BigDecimal monto, LocalDate fecha,
                              LocalDate fechaLimite, Instant ahora) {
        return new Deuda(UUID.randomUUID().toString(), nombre, tipo, monto, fecha, fechaLimite, ahora, false);
    }

    public Deuda editar(String nombre, BigDecimal monto, LocalDate fechaLimite, Instant ahora) {
        return new Deuda(id, nombre, tipo, monto, fecha, fechaLimite, ahora, eliminado);
    }

    public Deuda eliminar(Instant ahora) {
        return new Deuda(id, nombre, tipo, monto, fecha, fechaLimite, ahora, true);
    }
}
