package com.registrogastos.servicio;

import com.registrogastos.dominio.LineaPlan;
import com.registrogastos.dominio.Recurrencia;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;

/**
 * El plan del mes: el sueldo, lo que normalmente gastas (líneas que armas tú), cuánto queda
 * disponible y a qué lo asignas. Foto inmutable en moneda principal; la vista solo la pinta.
 *
 * @param ingresoEsperado el sueldo configurado, o lo registrado en Cuentas si no hay sueldo
 * @param ingresoFijado   true si el ingreso viene del ajuste (y no de lo registrado)
 * @param gastos          líneas del grupo GASTO, en su orden
 * @param asignaciones    líneas del grupo ASIGNACION, en su orden
 * @param gastadoReal     lo que de verdad se gastó en el mes (todo, con recibos y abonos)
 * @param gastadoRealPrimera lo gastado de verdad del día 1 al 15 (el resto es de la segunda)
 * @param sugeridas       recurrencias activas que todavía no están en el plan (por nombre)
 * @param quincenal       true si cobras por quincena: el sueldo se parte en dos y cada línea
 *                        cae en la quincena que le toca
 */
public record PlanMes(
        YearMonth mes,
        BigDecimal ingresoEsperado,
        boolean ingresoFijado,
        BigDecimal ingresoRegistrado,
        List<LineaPlan> gastos,
        List<LineaPlan> asignaciones,
        BigDecimal gastadoReal,
        BigDecimal gastadoRealPrimera,
        List<Recurrencia> sugeridas,
        boolean quincenal) {

    public PlanMes {
        gastos = List.copyOf(gastos);
        asignaciones = List.copyOf(asignaciones);
        sugeridas = List.copyOf(sugeridas);
    }

    public BigDecimal totalGastos() {
        return suma(gastos);
    }

    /** Sueldo menos los gastos del plan; negativo = los gastos no caben en el sueldo. */
    public BigDecimal disponible() {
        return ingresoEsperado.subtract(totalGastos());
    }

    public BigDecimal totalAsignado() {
        return suma(asignaciones);
    }

    /** Lo disponible que todavía no tiene destino; negativo = asignaste de más. */
    public BigDecimal sinAsignar() {
        return disponible().subtract(totalAsignado());
    }

    public boolean hayIngreso() {
        return ingresoEsperado.signum() > 0;
    }

    /** Qué parte del sueldo es un monto, de 0 a 1 (para barras y porcentajes). */
    public double parte(BigDecimal monto) {
        if (!hayIngreso()) {
            return 0;
        }
        return Math.clamp(monto.divide(ingresoEsperado, 4, RoundingMode.HALF_UP).doubleValue(), 0, 1);
    }

    // ---- Por quincena (el sueldo se parte a la mitad) ----

    public BigDecimal sueldoQuincena() {
        return ingresoEsperado.divide(BigDecimal.TWO, 2, RoundingMode.HALF_UP);
    }

    public BigDecimal gastosEn(LineaPlan.Quincena q) {
        return gastos.stream().map(l -> l.en(q)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal asignadoEn(LineaPlan.Quincena q) {
        return asignaciones.stream().map(l -> l.en(q)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal disponibleEn(LineaPlan.Quincena q) {
        return sueldoQuincena().subtract(gastosEn(q));
    }

    public BigDecimal sinAsignarEn(LineaPlan.Quincena q) {
        return disponibleEn(q).subtract(asignadoEn(q));
    }

    /** Lo gastado de verdad en la quincena: días 1 a 15 son la primera, el resto la segunda. */
    public BigDecimal gastadoRealEn(LineaPlan.Quincena q) {
        return q == LineaPlan.Quincena.PRIMERA ? gastadoRealPrimera : gastadoReal.subtract(gastadoRealPrimera);
    }

    /** Las líneas que caen en la quincena, con el monto que les toca ahí. */
    public List<LineaPlan> lineasEn(LineaPlan.Quincena q, LineaPlan.Grupo grupo) {
        return (grupo == LineaPlan.Grupo.GASTO ? gastos : asignaciones).stream()
                .filter(l -> l.en(q).signum() > 0)
                .toList();
    }

    /** Qué parte del sueldo DE LA QUINCENA es un monto, de 0 a 1. */
    public double parteQuincena(BigDecimal monto) {
        if (!hayIngreso()) {
            return 0;
        }
        return Math.clamp(monto.divide(sueldoQuincena(), 4, RoundingMode.HALF_UP).doubleValue(), 0, 1);
    }

    private static BigDecimal suma(List<LineaPlan> lineas) {
        return lineas.stream().map(LineaPlan::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
