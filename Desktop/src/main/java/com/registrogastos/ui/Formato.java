package com.registrogastos.ui;

import com.registrogastos.dominio.Recurrencia;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Cómo se muestran y se leen montos, fechas y porcentajes en la UI. */
final class Formato {

    private static final Locale ES = Locale.of("es");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES);
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMMM yyyy", ES);
    private static final DateTimeFormatter MES_SOLO = DateTimeFormatter.ofPattern("MMMM", ES);
    private static final DateTimeFormatter CORTA = DateTimeFormatter.ofPattern("d MMM", ES);

    private Formato() {
    }

    /** 1234.5 -> "1,234.50" (los separadores siguen la configuración regional de Windows). */
    static String monto(BigDecimal monto) {
        // DecimalFormat no es thread-safe: se crea uno por llamada en vez de compartirlo
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance()).format(monto);
    }

    /**
     * 1234.5 -> ["1,234", ".50"]: el saldo grande muestra el entero a tamaño completo y
     * los decimales más chicos, como los saldos de Binance y Apple Wallet.
     */
    static String[] montoPartido(BigDecimal monto) {
        String texto = monto(monto);
        char separador = DecimalFormatSymbols.getInstance().getDecimalSeparator();
        int corte = texto.lastIndexOf(separador);
        return new String[] {texto.substring(0, corte), texto.substring(corte)};
    }

    /** "Hoy", "Ayer" o "23 oct". */
    static String fechaCorta(LocalDate fecha, LocalDate hoy) {
        if (fecha.equals(hoy) || fecha.equals(hoy.minusDays(1))) {
            return dia(fecha, hoy);
        }
        return fecha.format(CORTA).replace(".", "");
    }

    /**
     * Lee el monto como lo escribe una persona: "50", "12.5", "12,50", "Q 1,250.00",
     * "$1.250,75". Se ignoran símbolos de moneda y espacios.
     *
     * Regla del separador decimal: si aparecen punto y coma, el último es el decimal; si
     * aparece uno solo, una vez y seguido de 1 o 2 dígitos, es decimal ("12,50"); en otro
     * caso es separador de miles ("1,250" = mil doscientos cincuenta).
     */
    static BigDecimal leerMonto(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("Escribe el monto del gasto");
        }
        String limpio = texto.replaceAll("[^0-9.,]", "");
        if (limpio.chars().noneMatch(Character::isDigit)) {
            throw new IllegalArgumentException("El monto lleva números, por ejemplo 12.50");
        }

        int separador = Math.max(limpio.lastIndexOf('.'), limpio.lastIndexOf(','));
        boolean hayAmbos = limpio.indexOf('.') >= 0 && limpio.indexOf(',') >= 0;
        boolean esDecimal = separador >= 0 && (hayAmbos
                || (limpio.indexOf(limpio.charAt(separador)) == separador
                    && limpio.length() - separador - 1 <= 2));

        String entero = (esDecimal ? limpio.substring(0, separador) : limpio).replaceAll("[.,]", "");
        String decimales = esDecimal ? limpio.substring(separador + 1) : "";
        if (decimales.length() > 2) {
            throw new IllegalArgumentException("El monto admite máximo 2 decimales");
        }
        return new BigDecimal((entero.isEmpty() ? "0" : entero) + (decimales.isEmpty() ? "" : "." + decimales));
    }

    static String mes(YearMonth mes) {
        return capitalizar(mes.format(MES));
    }

    static String nombreMes(YearMonth mes) {
        return mes.format(MES_SOLO);
    }

    /** "Octubre", para títulos. */
    static String tituloMes(YearMonth mes) {
        return capitalizar(nombreMes(mes));
    }

    static String dia(LocalDate fecha, LocalDate hoy) {
        if (fecha.equals(hoy)) {
            return "Hoy";
        }
        if (fecha.equals(hoy.minusDays(1))) {
            return "Ayer";
        }
        return capitalizar(fecha.format(DIA));
    }

    /** "Mensual", "Anual", "Cada 2 meses", "Cada 15 días", "Diaria". */
    static String frecuencia(Recurrencia r) {
        if (r.unidad() == Recurrencia.Unidad.MESES) {
            return switch (r.cada()) {
                case 1 -> "Mensual";
                case 12 -> "Anual";
                default -> "Cada " + r.cada() + " meses";
            };
        }
        return r.cada() == 1 ? "Diaria" : "Cada " + r.cada() + " días";
    }

    /** "Hoy", "Mañana", "En 3 días · 28 oct". */
    static String cuando(LocalDate fecha, LocalDate hoy) {
        long dias = ChronoUnit.DAYS.between(hoy, fecha);
        if (dias == 0) {
            return "Hoy";
        }
        if (dias == 1) {
            return "Mañana";
        }
        return "En " + dias + " días  ·  " + fecha.format(CORTA).replace(".", "");
    }

    static String porcentaje(double proporcion) {
        return Math.round(proporcion * 100) + "%";
    }

    private static String capitalizar(String texto) {
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
