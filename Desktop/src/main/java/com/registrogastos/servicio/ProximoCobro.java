package com.registrogastos.servicio;

import com.registrogastos.dominio.Recurrencia;

import java.time.LocalDate;

/** Un cobro que todavía no ocurre: qué recurrencia y en qué fecha. */
public record ProximoCobro(Recurrencia recurrencia, LocalDate fecha) {
}
