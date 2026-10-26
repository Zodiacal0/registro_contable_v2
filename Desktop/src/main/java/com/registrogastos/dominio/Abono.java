package com.registrogastos.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Dinero que entra a algo sin ser un gasto: un abono (o retiro) a una meta de ahorro, o un
 * abono recibido de alguien que me debe. Ambas tablas tienen la misma forma, así que comparten
 * este record; {@code duenoId} es la meta o la deuda a la que pertenece.
 *
 * @param monto positivo = entra a la meta / me pagan; negativo = retiro de la meta
 */
public record Abono(
        String id,
        String duenoId,
        BigDecimal monto,
        LocalDate fecha,
        Instant creadoEn,
        Instant actualizadoEn,
        boolean eliminado) {

    public Abono {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(duenoId, "duenoId");
        Objects.requireNonNull(fecha, "fecha");
        Objects.requireNonNull(creadoEn, "creadoEn");
        Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        monto = Montos.distintoDeCero(monto);
    }

    public static Abono nuevo(String duenoId, BigDecimal monto, LocalDate fecha, Instant ahora) {
        return new Abono(UUID.randomUUID().toString(), duenoId, monto, fecha, ahora, ahora, false);
    }

    public Abono eliminar(Instant ahora) {
        return new Abono(id, duenoId, monto, fecha, creadoEn, ahora, true);
    }
}
