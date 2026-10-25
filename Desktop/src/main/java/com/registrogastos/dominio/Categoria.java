package com.registrogastos.dominio;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Agrupa gastos (Comida, Transporte, Servicios...).
 * Es un record: inmutable; para "cambiarla" se crea una copia nueva.
 */
public record Categoria(String id, String nombre, Instant actualizadoEn, boolean eliminado) {

    /** Constructor compacto: valida y normaliza antes de asignar los campos. */
    public Categoria {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre de la categoría no puede estar vacío");
        }
        nombre = nombre.strip();
    }

    public static Categoria nueva(String nombre, Instant ahora) {
        return new Categoria(UUID.randomUUID().toString(), nombre, ahora, false);
    }

    public Categoria renombrar(String nuevoNombre, Instant ahora) {
        return new Categoria(id, nuevoNombre, ahora, eliminado);
    }

    public Categoria eliminar(Instant ahora) {
        return new Categoria(id, nombre, ahora, true);
    }
}
