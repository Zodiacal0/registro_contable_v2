package com.registrogastos.ui;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Formato es package-private: la prueba vive en el mismo paquete para poder usarlo. */
class FormatoTest {

    // @ParameterizedTest corre la misma prueba con cada fila: entrada | esperado
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "50          | 50",
            "12.5        | 12.5",
            "12,50       | 12.50",
            "Q 50        | 50",
            "$12.99      | 12.99",
            "1,250       | 1250",
            "1.250       | 1250",
            "1,250.75    | 1250.75",
            "Q 1.250,75  | 1250.75",
            "1,250,000   | 1250000",
            ".5          | 0.5",
    })
    void leeMontosComoLosEscribeUnaPersona(String texto, String esperado) {
        assertEquals(new BigDecimal(esperado), Formato.leerMonto(texto));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "abc", "Q", "12,345.678"})
    void rechazaLoQueNoEsUnMonto(String texto) {
        assertThrows(IllegalArgumentException.class, () -> Formato.leerMonto(texto));
    }
}
