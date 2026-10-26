package com.registrogastos.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Una línea del plan mensual: algo que esperas cada mes, con su monto.
 *
 * @param grupo    GASTO = lo que normalmente pagas; ASIGNACION = a qué va lo que queda disponible
 * @param quincena con qué quincena se paga (solo importa si cobras por quincena)
 * @param orden    posición en su grupo (el de creación)
 */
public record LineaPlan(String id, String nombre, BigDecimal monto, Grupo grupo, Quincena quincena, int orden,
                        Instant actualizadoEn, boolean eliminado) {

    public enum Grupo { GASTO, ASIGNACION }

    public enum Quincena {
        PRIMERA("1ª"), SEGUNDA("2ª"), AMBAS("Mitad y mitad");

        private final String nombre;

        Quincena(String nombre) {
            this.nombre = nombre;
        }

        public String nombre() {
            return nombre;
        }
    }

    public LineaPlan {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(grupo, "grupo");
        Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        quincena = quincena == null ? Quincena.AMBAS : quincena;
        nombre = Montos.textoObligatorio(nombre, "Escribe el nombre del gasto");
        monto = Montos.positivo(monto);
    }

    public static LineaPlan nueva(String nombre, BigDecimal monto, Grupo grupo, int orden, Instant ahora) {
        return new LineaPlan(UUID.randomUUID().toString(), nombre, monto, grupo, Quincena.AMBAS, orden, ahora, false);
    }

    /** Cuánto de esta línea cae en la quincena dada: todo, nada o la mitad. */
    public BigDecimal en(Quincena cual) {
        return switch (quincena) {
            case AMBAS -> monto.divide(BigDecimal.TWO, 2, RoundingMode.HALF_UP);
            case PRIMERA, SEGUNDA -> quincena == cual ? monto : BigDecimal.ZERO;
        };
    }

    public LineaPlan editar(String nombre, BigDecimal monto, Instant ahora) {
        return new LineaPlan(id, nombre, monto, grupo, quincena, orden, ahora, eliminado);
    }

    public LineaPlan conQuincena(Quincena quincena, Instant ahora) {
        return new LineaPlan(id, nombre, monto, grupo, quincena, orden, ahora, eliminado);
    }

    public LineaPlan eliminar(Instant ahora) {
        return new LineaPlan(id, nombre, monto, grupo, quincena, orden, ahora, true);
    }
}
