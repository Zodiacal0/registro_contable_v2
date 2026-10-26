package com.registrogastos.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Dinero que entra a una cuenta (salario, una venta, un regalo). En la moneda de la cuenta. */
public record Ingreso(
        String id,
        String cuentaId,
        String descripcion,
        BigDecimal monto,
        LocalDate fecha,
        Instant creadoEn,
        Instant actualizadoEn,
        boolean eliminado) {

    public Ingreso {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(cuentaId, "cuentaId");
        Objects.requireNonNull(fecha, "fecha");
        Objects.requireNonNull(creadoEn, "creadoEn");
        Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        descripcion = Montos.textoObligatorio(descripcion, "La descripción no puede estar vacía");
        monto = Montos.positivo(monto);
    }

    public static Ingreso nuevo(String cuentaId, String descripcion, BigDecimal monto, LocalDate fecha, Instant ahora) {
        return new Ingreso(UUID.randomUUID().toString(), cuentaId, descripcion, monto, fecha, ahora, ahora, false);
    }

    public Ingreso eliminar(Instant ahora) {
        return new Ingreso(id, cuentaId, descripcion, monto, fecha, creadoEn, ahora, true);
    }
}
