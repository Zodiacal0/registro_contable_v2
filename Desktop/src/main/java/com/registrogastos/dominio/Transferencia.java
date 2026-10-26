package com.registrogastos.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Dinero que pasa de una cuenta a otra. No es gasto ni ingreso: el total no cambia, solo dónde
 * está. Lleva dos montos porque las monedas pueden ser distintas (salen US$ 100, entran Q 770);
 * si son iguales, ambos montos coinciden.
 */
public record Transferencia(
        String id,
        String origenId,
        String destinoId,
        BigDecimal salida,
        BigDecimal entrada,
        LocalDate fecha,
        Instant creadoEn,
        Instant actualizadoEn,
        boolean eliminado) {

    public Transferencia {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origenId, "origenId");
        Objects.requireNonNull(destinoId, "destinoId");
        Objects.requireNonNull(fecha, "fecha");
        Objects.requireNonNull(creadoEn, "creadoEn");
        Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        if (origenId.equals(destinoId)) {
            throw new IllegalArgumentException("Elige una cuenta de destino distinta a la de origen");
        }
        salida = Montos.positivo(salida);
        entrada = Montos.positivo(entrada);
    }

    public static Transferencia nueva(String origenId, String destinoId, BigDecimal salida, BigDecimal entrada,
                                      LocalDate fecha, Instant ahora) {
        return new Transferencia(UUID.randomUUID().toString(), origenId, destinoId, salida, entrada, fecha,
                ahora, ahora, false);
    }

    public Transferencia eliminar(Instant ahora) {
        return new Transferencia(id, origenId, destinoId, salida, entrada, fecha, creadoEn, ahora, true);
    }
}
