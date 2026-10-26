package com.registrogastos.servicio;

import com.registrogastos.dominio.Categoria;

import java.math.BigDecimal;

/**
 * Cuánto se gastó en una categoría dentro de un mes.
 *
 * @param categoria   null = gastos sin categoría (o cuya categoría fue eliminada)
 * @param movimientos cuántos gastos suman ese total
 * @param proporcion  de 0 a 1 respecto al total del mes
 */
public record TotalCategoria(Categoria categoria, BigDecimal total, int movimientos, double proporcion) {

    public boolean sinCategoria() {
        return categoria == null;
    }
}
