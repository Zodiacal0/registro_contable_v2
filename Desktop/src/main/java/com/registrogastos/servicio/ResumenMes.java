package com.registrogastos.servicio;

import com.registrogastos.dominio.Categoria;
import com.registrogastos.dominio.Gasto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Foto inmutable de un mes: todo lo que la pantalla de resumen necesita, ya calculado.
 * La UI no suma ni agrupa: solo pinta esto.
 *
 * Los totales están en moneda principal; cada gasto conserva su monto original (en la moneda
 * de su cuenta) y {@link #enPrincipal(Gasto)} lo convierte.
 *
 * @param diasConsiderados días para el promedio: hoy si es el mes en curso, si no el mes completo
 * @param conversor        la foto de monedas con que se calcularon los totales
 */
public record ResumenMes(
        YearMonth mes,
        List<Gasto> gastos,
        List<Categoria> categorias,
        BigDecimal total,
        BigDecimal totalMesAnterior,
        int diasConsiderados,
        List<TotalCategoria> porCategoria,
        Conversor conversor) {

    public ResumenMes {
        // Copias defensivas: quien construyó el resumen ya no puede modificar sus listas
        gastos = List.copyOf(gastos);
        categorias = List.copyOf(categorias);
        porCategoria = List.copyOf(porCategoria);
    }

    /** Variación porcentual contra el mes anterior; vacío si el mes anterior no tuvo gastos. */
    public Optional<BigDecimal> variacion() {
        if (totalMesAnterior.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(total.subtract(totalMesAnterior)
                .multiply(BigDecimal.valueOf(100))
                .divide(totalMesAnterior, 1, RoundingMode.HALF_UP));
    }

    public BigDecimal promedioDiario() {
        return total.divide(BigDecimal.valueOf(diasConsiderados), 2, RoundingMode.HALF_UP);
    }

    /** Total de cada día del mes; el índice 0 es el día 1. Los días sin gastos valen cero. */
    public List<BigDecimal> porDia() {
        var dias = new ArrayList<>(Collections.nCopies(mes.lengthOfMonth(), BigDecimal.ZERO));
        for (Gasto gasto : gastos) {
            int i = gasto.fecha().getDayOfMonth() - 1;
            dias.set(i, dias.get(i).add(enPrincipal(gasto)));
        }
        return List.copyOf(dias);
    }

    public Optional<Gasto> mayorGasto() {
        return gastos.stream().max(Comparator.comparing(this::enPrincipal));
    }

    public BigDecimal enPrincipal(Gasto gasto) {
        return conversor.aPrincipal(gasto);
    }

    /** Si algún gasto del mes está en otra moneda (para avisar que el total está convertido). */
    public boolean hayOtraMoneda() {
        return gastos.stream().anyMatch(g -> !conversor.monedaDe(g).esPrincipal());
    }

    /** Categoría activa por id; vacío si es null o fue eliminada. */
    public Optional<Categoria> categoria(String id) {
        return categorias.stream().filter(c -> c.id().equals(id)).findFirst();
    }
}
