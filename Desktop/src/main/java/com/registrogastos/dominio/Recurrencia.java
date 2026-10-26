package com.registrogastos.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Un gasto que se repite: Netflix cada mes, el dominio cada año, el agua cada 2 meses.
 *
 * @param cada          cuántas unidades entre cobros (1 = cada mes si la unidad es MESES)
 * @param inicio        fecha del primer cobro; todas las demás se calculan desde aquí
 * @param generadaHasta hasta qué fecha ya se generaron sus gastos; null = ninguno todavía
 * @param activa        false = en pausa: no genera cobros
 */
public record Recurrencia(
        String id,
        String descripcion,
        BigDecimal monto,
        String categoriaId,
        int cada,
        Unidad unidad,
        LocalDate inicio,
        LocalDate generadaHasta,
        boolean activa,
        Instant actualizadoEn,
        boolean eliminado) {

    public enum Unidad { DIAS, MESES }

    /** Días promedio de un mes (365.25 / 12): para estimar el costo mensual de lo diario. */
    private static final BigDecimal DIAS_POR_MES = new BigDecimal("30.4375");

    public Recurrencia {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        if (cada <= 0) {
            throw new IllegalArgumentException("La frecuencia debe ser de al menos 1");
        }
        Objects.requireNonNull(unidad, "unidad");
        descripcion = Montos.textoObligatorio(descripcion, "La descripción no puede estar vacía");
        monto = Montos.positivo(monto);
    }

    public static Recurrencia nueva(String descripcion, BigDecimal monto, String categoriaId,
                                    int cada, Unidad unidad, LocalDate inicio, Instant ahora) {
        return new Recurrencia(UUID.randomUUID().toString(), descripcion, monto, categoriaId,
                cada, unidad, inicio, null, true, ahora, false);
    }

    // ---- Fechas ----

    /**
     * Fecha del cobro número k (k = 0 es el primero). Se calcula SIEMPRE desde el inicio:
     * si se sumara un mes al cobro anterior, un cobro del 31 caería en 28 en febrero y se
     * quedaría en 28 para siempre. plusMonths ajusta solo al último día cuando hace falta.
     */
    public LocalDate cobro(int k) {
        long pasos = (long) k * cada;
        return unidad == Unidad.DIAS ? inicio.plusDays(pasos) : inicio.plusMonths(pasos);
    }

    /** Cobros con fecha en (desde, hasta]: el primero excluido, el último incluido. */
    public List<LocalDate> cobrosEntre(LocalDate desdeExcluido, LocalDate hastaIncluido) {
        var fechas = new ArrayList<LocalDate>();
        for (int k = 0; !cobro(k).isAfter(hastaIncluido); k++) {
            if (desdeExcluido == null || cobro(k).isAfter(desdeExcluido)) {
                fechas.add(cobro(k));
            }
        }
        return fechas;
    }

    /** Primer cobro en la fecha dada o después. */
    public LocalDate proximoCobro(LocalDate desde) {
        int k = 0;
        while (cobro(k).isBefore(desde)) {
            k++;
        }
        return cobro(k);
    }

    /** Costo aproximado por mes, para comparar recurrencias de distinta frecuencia. */
    public BigDecimal costoMensual() {
        BigDecimal porUnidad = monto.divide(BigDecimal.valueOf(cada), 6, RoundingMode.HALF_UP);
        return (unidad == Unidad.MESES ? porUnidad : porUnidad.multiply(DIAS_POR_MES))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Id del gasto que genera esta recurrencia en una fecha. Es determinístico (UUID v3 a
     * partir de "recurrencia|fecha"): si el celular y la PC generan el mismo cobro, producen
     * el mismo id y la sincronización ve un solo gasto, no dos.
     */
    public String idGasto(LocalDate fecha) {
        return UUID.nameUUIDFromBytes((id + "|" + fecha).getBytes(StandardCharsets.UTF_8)).toString();
    }

    // ---- Cambios (devuelven una copia) ----

    public Recurrencia editar(String descripcion, BigDecimal monto, String categoriaId,
                              int cada, Unidad unidad, Instant ahora) {
        return new Recurrencia(id, descripcion, monto, categoriaId, cada, unidad, inicio,
                generadaHasta, activa, ahora, eliminado);
    }

    public Recurrencia generadaHasta(LocalDate fecha, Instant ahora) {
        return new Recurrencia(id, descripcion, monto, categoriaId, cada, unidad, inicio,
                fecha, activa, ahora, eliminado);
    }

    public Recurrencia pausar(Instant ahora) {
        return new Recurrencia(id, descripcion, monto, categoriaId, cada, unidad, inicio,
                generadaHasta, false, ahora, eliminado);
    }

    /**
     * Al reanudar se salta lo que venció en pausa; si justo hoy toca cobro, ese sí se genera
     * (por eso se marca "generada hasta ayer").
     */
    public Recurrencia reanudar(LocalDate hoy, Instant ahora) {
        LocalDate ayer = hoy.minusDays(1);
        LocalDate desde = generadaHasta == null || generadaHasta.isBefore(ayer) ? ayer : generadaHasta;
        return new Recurrencia(id, descripcion, monto, categoriaId, cada, unidad, inicio,
                desde, true, ahora, eliminado);
    }

    public Recurrencia eliminar(Instant ahora) {
        return new Recurrencia(id, descripcion, monto, categoriaId, cada, unidad, inicio,
                generadaHasta, false, ahora, true);
    }
}
