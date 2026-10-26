package com.registrogastos.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Una meta de ahorro: juntar {@code objetivo} para algo, opcionalmente antes de una fecha.
 * Lo ahorrado no vive aquí: se calcula sumando sus aportes.
 *
 * @param color #RRGGBB de la paleta, como las categorías
 */
public record Meta(
        String id,
        String nombre,
        BigDecimal objetivo,
        LocalDate fechaObjetivo,
        String color,
        Instant actualizadoEn,
        boolean eliminado) {

    public Meta {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        nombre = Montos.textoObligatorio(nombre, "El nombre de la meta no puede estar vacío");
        objetivo = Montos.positivo(objetivo);
        if (color == null || !color.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Color inválido: " + color);
        }
        color = color.toLowerCase();
    }

    public static Meta nueva(String nombre, BigDecimal objetivo, LocalDate fechaObjetivo, String color, Instant ahora) {
        return new Meta(UUID.randomUUID().toString(), nombre, objetivo, fechaObjetivo, color, ahora, false);
    }

    public Meta editar(String nombre, BigDecimal objetivo, LocalDate fechaObjetivo, Instant ahora) {
        return new Meta(id, nombre, objetivo, fechaObjetivo, color, ahora, eliminado);
    }

    public Meta eliminar(Instant ahora) {
        return new Meta(id, nombre, objetivo, fechaObjetivo, color, ahora, true);
    }
}
