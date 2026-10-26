package com.registrogastos.servicio;

import com.registrogastos.dominio.Meta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Una meta con su progreso calculado. Lo único guardado es {@code ahorrado} (la suma de
 * aportes); todo lo demás se deriva, así nunca puede quedar desincronizado.
 */
public record EstadoMeta(Meta meta, BigDecimal ahorrado, LocalDate hoy) {

    public BigDecimal restante() {
        return meta.objetivo().subtract(ahorrado).max(BigDecimal.ZERO);
    }

    /** De 0 a 1 (puede pasarse de 1 si se ahorró de más; la UI lo topa). */
    public double proporcion() {
        return ahorrado.divide(meta.objetivo(), 4, RoundingMode.HALF_UP).doubleValue();
    }

    public boolean completada() {
        return ahorrado.compareTo(meta.objetivo()) >= 0;
    }

    /**
     * Cuánto aportar por mes para llegar a tiempo: lo que falta entre los meses que quedan
     * (contando el actual). Vacío si no tiene fecha o ya se completó.
     */
    public Optional<BigDecimal> aporteMensualSugerido() {
        if (meta.fechaObjetivo() == null || completada()) {
            return Optional.empty();
        }
        long meses = ChronoUnit.MONTHS.between(YearMonth.from(hoy), YearMonth.from(meta.fechaObjetivo())) + 1;
        return Optional.of(restante().divide(BigDecimal.valueOf(Math.max(1, meses)), 2, RoundingMode.HALF_UP));
    }
}
