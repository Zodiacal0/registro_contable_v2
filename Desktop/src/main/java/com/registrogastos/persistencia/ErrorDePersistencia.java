package com.registrogastos.persistencia;

/**
 * Envuelve las SQLException (checked) en una excepción unchecked, para que las
 * capas de arriba no tengan que declarar "throws SQLException" ni conocer JDBC.
 */
public class ErrorDePersistencia extends RuntimeException {

    public ErrorDePersistencia(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
