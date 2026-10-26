package com.registrogastos.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * De dónde sale (o adónde entra) el dinero: efectivo, una cuenta de banco, una tarjeta.
 * El saldo no vive aquí: se calcula a partir del saldo inicial y los movimientos.
 *
 * La moneda no se puede cambiar después de crearla: todos los montos ya registrados en la
 * cuenta se reinterpretarían (100 quetzales pasarían a ser 100 dólares).
 *
 * @param saldoInicial lo que había al empezar a usar la app; puede ser negativo (tarjeta)
 */
public record Cuenta(
        String id,
        String nombre,
        Tipo tipo,
        Moneda moneda,
        BigDecimal saldoInicial,
        Instant actualizadoEn,
        boolean eliminado) {

    /** La cuenta que crea la migración V7; id fijo para que sea la misma en todos los equipos. */
    public static final String PRINCIPAL_ID = "00000000-0000-4000-8000-000000000001";

    public enum Tipo {
        EFECTIVO("Efectivo"), BANCO("Banco"), TARJETA("Tarjeta"), OTRA("Otra");

        private final String nombre;

        Tipo(String nombre) {
            this.nombre = nombre;
        }

        public String nombre() {
            return nombre;
        }
    }

    public Cuenta {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(moneda, "moneda");
        Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        nombre = Montos.textoObligatorio(nombre, "El nombre de la cuenta no puede estar vacío");
        saldoInicial = Montos.dosDecimales(saldoInicial == null ? BigDecimal.ZERO : saldoInicial);
    }

    public static Cuenta nueva(String nombre, Tipo tipo, Moneda moneda, BigDecimal saldoInicial, Instant ahora) {
        return new Cuenta(UUID.randomUUID().toString(), nombre, tipo, moneda, saldoInicial, ahora, false);
    }

    public Cuenta editar(String nombre, Tipo tipo, BigDecimal saldoInicial, Instant ahora) {
        return new Cuenta(id, nombre, tipo, moneda, saldoInicial, ahora, eliminado);
    }

    public Cuenta eliminar(Instant ahora) {
        return new Cuenta(id, nombre, tipo, moneda, saldoInicial, ahora, true);
    }
}
