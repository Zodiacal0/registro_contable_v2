package com.registrogastos.servicio;

import com.registrogastos.dominio.Cuenta;

import java.math.BigDecimal;

/**
 * Una cuenta con su saldo ya calculado.
 *
 * @param saldo          en la moneda de la cuenta
 * @param saldoPrincipal convertido a la moneda principal (para sumar el patrimonio)
 */
public record EstadoCuenta(Cuenta cuenta, BigDecimal saldo, BigDecimal saldoPrincipal) {
}
