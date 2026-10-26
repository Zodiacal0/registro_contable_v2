package com.registrogastos.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Un gasto registrado. El dinero va en BigDecimal (nunca double: 0.1 + 0.2 != 0.3).
 *
 * @param categoriaId   puede ser null (gasto sin categoría)
 * @param recurrenciaId de qué recurrencia salió; null = gasto registrado a mano
 * @param deudaId       si es un abono, a qué deuda (tipo DEBO) pagó; null = no es abono
 */
public record Gasto(
        String id,
        String descripcion,
        BigDecimal monto,
        LocalDate fecha,
        String categoriaId,
        String recurrenciaId,
        String deudaId,
        Instant creadoEn,
        Instant actualizadoEn,
        boolean eliminado) {

    public Gasto {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fecha, "fecha");
        Objects.requireNonNull(creadoEn, "creadoEn");
        Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        descripcion = Montos.textoObligatorio(descripcion, "La descripción no puede estar vacía");
        monto = Montos.positivo(monto); // 5 -> 5.00
    }

    public static Gasto nuevo(String descripcion, BigDecimal monto, LocalDate fecha,
                              String categoriaId, Instant ahora) {
        return new Gasto(UUID.randomUUID().toString(), descripcion, monto, fecha,
                categoriaId, null, null, ahora, ahora, false);
    }

    /** El cobro de una recurrencia en una fecha; su id es determinístico (ver Recurrencia.idGasto). */
    public static Gasto deRecurrencia(Recurrencia recurrencia, LocalDate fecha, Instant ahora) {
        return new Gasto(recurrencia.idGasto(fecha), recurrencia.descripcion(), recurrencia.monto(),
                fecha, recurrencia.categoriaId(), recurrencia.id(), null, ahora, ahora, false);
    }

    /**
     * Abono a una deuda que debo: sale dinero, así que es un gasto, ligado a su deuda.
     *
     * @param descripcion la del usuario; si viene vacía o null, "Abono · nombre de la deuda"
     */
    public static Gasto abono(Deuda deuda, String descripcion, BigDecimal monto, LocalDate fecha,
                              String categoriaId, Instant ahora) {
        String texto = descripcion == null || descripcion.isBlank() ? "Abono · " + deuda.nombre() : descripcion;
        return new Gasto(UUID.randomUUID().toString(), texto, monto, fecha,
                categoriaId, null, deuda.id(), ahora, ahora, false);
    }

    public boolean esAbono() {
        return deudaId != null;
    }

    public boolean esRecurrente() {
        return recurrenciaId != null;
    }

    public Gasto editar(String descripcion, BigDecimal monto, LocalDate fecha,
                        String categoriaId, Instant ahora) {
        return new Gasto(id, descripcion, monto, fecha, categoriaId, recurrenciaId, deudaId, creadoEn, ahora, eliminado);
    }

    public Gasto eliminar(Instant ahora) {
        return new Gasto(id, descripcion, monto, fecha, categoriaId, recurrenciaId, deudaId, creadoEn, ahora, true);
    }
}
