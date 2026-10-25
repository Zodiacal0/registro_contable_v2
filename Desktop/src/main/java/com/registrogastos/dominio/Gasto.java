package com.registrogastos.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Un gasto registrado. El dinero va en BigDecimal (nunca double: 0.1 + 0.2 != 0.3).
 *
 * @param categoriaId puede ser null (gasto sin categoría)
 */
public record Gasto(
        String id,
        String descripcion,
        BigDecimal monto,
        LocalDate fecha,
        String categoriaId,
        Instant creadoEn,
        Instant actualizadoEn,
        boolean eliminado) {

    public Gasto {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fecha, "fecha");
        Objects.requireNonNull(creadoEn, "creadoEn");
        Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("La descripción no puede estar vacía");
        }
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor que cero");
        }
        if (monto.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("El monto admite máximo 2 decimales: " + monto);
        }
        descripcion = descripcion.strip();
        monto = monto.setScale(2, RoundingMode.UNNECESSARY); // 5 -> 5.00
    }

    public static Gasto nuevo(String descripcion, BigDecimal monto, LocalDate fecha,
                              String categoriaId, Instant ahora) {
        return new Gasto(UUID.randomUUID().toString(), descripcion, monto, fecha,
                categoriaId, ahora, ahora, false);
    }

    public Gasto editar(String descripcion, BigDecimal monto, LocalDate fecha,
                        String categoriaId, Instant ahora) {
        return new Gasto(id, descripcion, monto, fecha, categoriaId, creadoEn, ahora, eliminado);
    }

    public Gasto eliminar(Instant ahora) {
        return new Gasto(id, descripcion, monto, fecha, categoriaId, creadoEn, ahora, true);
    }
}
