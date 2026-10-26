package com.registrogastos.dominio;

/**
 * Monedas soportadas. La principal es en la que se suman los totales (resumen, analíticas);
 * las demás se convierten con el tipo de cambio que define el usuario.
 */
public enum Moneda {
    GTQ("Q", "Quetzales"),
    USD("US$", "Dólares");

    public static final Moneda PRINCIPAL = GTQ;

    private final String simbolo;
    private final String nombre;

    Moneda(String simbolo, String nombre) {
        this.simbolo = simbolo;
        this.nombre = nombre;
    }

    public String simbolo() {
        return simbolo;
    }

    public String nombre() {
        return nombre;
    }

    public boolean esPrincipal() {
        return this == PRINCIPAL;
    }
}
