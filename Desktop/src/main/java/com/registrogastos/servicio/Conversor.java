package com.registrogastos.servicio;

import com.registrogastos.dominio.Gasto;
import com.registrogastos.dominio.Moneda;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Foto de "en qué moneda está cada cuenta y a cuánto se cambia": con ella, cualquier monto se
 * lleva a la moneda principal para sumarlo. Es inmutable, así que un resumen ya calculado no
 * cambia si después se edita el tipo de cambio.
 *
 * Se usa el tipo de cambio ACTUAL para todo, incluso gastos viejos: es más simple y para una
 * app personal basta. Lo exacto sería guardar el cambio del día en cada gasto.
 *
 * @param tipoDeCambio cuántas unidades de la moneda principal vale 1 de cada otra moneda
 */
public record Conversor(Map<String, Moneda> monedaDeCuenta, Map<Moneda, BigDecimal> tipoDeCambio) {

    /** Sin cuentas en otra moneda: todo se toma como moneda principal (útil en pruebas). */
    public static final Conversor SOLO_PRINCIPAL = new Conversor(Map.of(), Map.of());

    public Conversor {
        monedaDeCuenta = Map.copyOf(monedaDeCuenta);
        tipoDeCambio = Map.copyOf(tipoDeCambio);
    }

    public Moneda monedaDe(String cuentaId) {
        return monedaDeCuenta.getOrDefault(cuentaId, Moneda.PRINCIPAL);
    }

    public Moneda monedaDe(Gasto gasto) {
        return monedaDe(gasto.cuentaId());
    }

    public BigDecimal tasa(Moneda moneda) {
        return moneda.esPrincipal() ? BigDecimal.ONE : tipoDeCambio.getOrDefault(moneda, BigDecimal.ONE);
    }

    public BigDecimal aPrincipal(BigDecimal monto, Moneda moneda) {
        return moneda.esPrincipal() ? monto : monto.multiply(tasa(moneda)).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal aPrincipal(Gasto gasto) {
        return aPrincipal(gasto.monto(), monedaDe(gasto));
    }

    /** De una moneda a otra pasando por la principal (para sugerir el monto de una transferencia). */
    public BigDecimal convertir(BigDecimal monto, Moneda de, Moneda a) {
        if (de == a) {
            return monto;
        }
        return aPrincipal(monto, de).divide(tasa(a), 2, RoundingMode.HALF_UP);
    }
}
