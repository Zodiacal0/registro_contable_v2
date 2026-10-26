package com.registrogastos.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Validación de montos compartida por todo el dominio (gastos, recurrencias, metas, deudas):
 * mayor que cero y con máximo 2 decimales. Antes vivía copiada en cada record.
 */
public final class Montos {

    private Montos() {
    }

    /** Devuelve el monto normalizado a 2 decimales (5 -> 5.00) o lanza si no es válido. */
    public static BigDecimal positivo(BigDecimal monto) {
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor que cero");
        }
        return conDosDecimales(monto);
    }

    /** Igual, pero acepta negativos (p. ej. un retiro de una meta). Cero no. */
    public static BigDecimal distintoDeCero(BigDecimal monto) {
        if (monto == null || monto.signum() == 0) {
            throw new IllegalArgumentException("El monto no puede ser cero");
        }
        return conDosDecimales(monto);
    }

    /** Texto sin espacios sobrantes, o lanza con el mensaje dado (completo: el género varía). */
    public static String textoObligatorio(String texto, String mensajeSiFalta) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException(mensajeSiFalta);
        }
        return texto.strip();
    }

    private static BigDecimal conDosDecimales(BigDecimal monto) {
        if (monto.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("El monto admite máximo 2 decimales: " + monto);
        }
        return monto.setScale(2, RoundingMode.UNNECESSARY);
    }
}
