package com.registrogastos.ui;

import com.registrogastos.dominio.Categoria;

/** Colores de categoría para la UI. El color de cada categoría viene de la base. */
final class Paleta {

    /** Más oscuro que cualquier color de categoría: "sin categoría" no compite con las demás. */
    static final String SIN_CATEGORIA = "#5e5669";

    private Paleta() {
    }

    static String colorDe(Categoria categoria) {
        return categoria == null ? SIN_CATEGORIA : categoria.color();
    }
}
