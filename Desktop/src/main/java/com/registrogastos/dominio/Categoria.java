package com.registrogastos.dominio;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Agrupa gastos (Comida, Transporte, Servicios...).
 * Es un record: inmutable; para "cambiarla" se crea una copia nueva.
 *
 * @param color #RRGGBB; se guarda en la base para que sea igual en todos los dispositivos
 */
public record Categoria(String id, String nombre, String color, Instant actualizadoEn, boolean eliminado) {

    /**
     * Colores disponibles, en orden de asignación: tintes de la familia gris violácea de la
     * UI. Los que tienen luminosidad parecida se separan por tono (lavanda / malva / pervinca)
     * y el orden alterna claros y oscuros para que dos categorías seguidas contrasten.
     * Ninguno es #E2DDE7: ese es el color del texto y de la acción principal.
     */
    public static final List<String> PALETA = List.of(
            "#cbbfe0", "#8a7aa8", "#d9a9c4", "#6f6488", "#a6b0d9", "#b38aa6");

    /** Constructor compacto: valida y normaliza antes de asignar los campos. */
    public Categoria {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre de la categoría no puede estar vacío");
        }
        if (color == null || !color.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Color inválido: " + color);
        }
        nombre = nombre.strip();
        color = color.toLowerCase();
    }

    public static Categoria nueva(String nombre, String color, Instant ahora) {
        return new Categoria(UUID.randomUUID().toString(), nombre, color, ahora, false);
    }

    public Categoria renombrar(String nuevoNombre, Instant ahora) {
        return new Categoria(id, nuevoNombre, color, ahora, eliminado);
    }

    public Categoria eliminar(Instant ahora) {
        return new Categoria(id, nombre, color, ahora, true);
    }
}
