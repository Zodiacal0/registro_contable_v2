package com.registrogastos.servicio;

import com.registrogastos.dominio.Abono;
import com.registrogastos.dominio.Categoria;
import com.registrogastos.dominio.Meta;
import com.registrogastos.persistencia.AbonoRepositorio;
import com.registrogastos.persistencia.MetaRepositorio;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/** Casos de uso de las metas de ahorro. Los aportes no son gastos: no tocan el resumen del mes. */
public final class MetaServicio {

    private final MetaRepositorio metas;
    private final AbonoRepositorio aportes;
    private final Clock reloj;

    public MetaServicio(MetaRepositorio metas, AbonoRepositorio aportes, Clock reloj) {
        this.metas = metas;
        this.aportes = aportes;
        this.reloj = reloj;
    }

    public Meta crear(String nombre, BigDecimal objetivo, LocalDate fechaObjetivo) {
        validarFecha(fechaObjetivo);
        var meta = Meta.nueva(nombre, objetivo, fechaObjetivo, siguienteColor(), reloj.instant());
        metas.guardar(meta);
        return meta;
    }

    public void editar(String id, String nombre, BigDecimal objetivo, LocalDate fechaObjetivo) {
        validarFecha(fechaObjetivo);
        metas.guardar(buscar(id).editar(nombre, objetivo, fechaObjetivo, reloj.instant()));
    }

    /** Borrado lógico; sus aportes quedan como historial pero ya no se muestran. */
    public void eliminar(String id) {
        metas.guardar(buscar(id).eliminar(reloj.instant()));
    }

    public void aportar(String metaId, BigDecimal monto, LocalDate fecha) {
        buscar(metaId);
        aportes.guardar(Abono.nuevo(metaId, monto, fecha, reloj.instant()));
    }

    /** Sacar dinero de la meta: se guarda como aporte negativo. */
    public void retirar(String metaId, BigDecimal monto, LocalDate fecha) {
        BigDecimal ahorrado = ahorrado(metaId);
        if (monto.compareTo(ahorrado) > 0) {
            throw new IllegalArgumentException("No puedes retirar más de lo ahorrado (" + ahorrado + ")");
        }
        aportes.guardar(Abono.nuevo(metaId, monto.negate(), fecha, reloj.instant()));
    }

    public void eliminarAporte(String aporteId) {
        aportes.buscarPorId(aporteId).ifPresent(a -> aportes.guardar(a.eliminar(reloj.instant())));
    }

    /** Metas con su progreso: primero las que faltan (por avance), al final las completadas. */
    public List<EstadoMeta> listar() {
        LocalDate hoy = LocalDate.now(reloj);
        return metas.listarActivas().stream()
                .map(m -> new EstadoMeta(m, ahorrado(m.id()), hoy))
                .sorted(Comparator.comparing(EstadoMeta::completada)
                        .thenComparing(Comparator.comparingDouble(EstadoMeta::proporcion).reversed()))
                .toList();
    }

    public List<Abono> aportesDe(String metaId) {
        return aportes.listarDe(metaId);
    }

    private BigDecimal ahorrado(String metaId) {
        return aportes.listarDe(metaId).stream().map(Abono::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Meta buscar(String id) {
        return metas.buscarPorId(id)
                .filter(m -> !m.eliminado())
                .orElseThrow(() -> new NoSuchElementException("No existe la meta " + id));
    }

    private void validarFecha(LocalDate fechaObjetivo) {
        if (fechaObjetivo != null && fechaObjetivo.isBefore(LocalDate.now(reloj))) {
            throw new IllegalArgumentException("La fecha objetivo no puede estar en el pasado");
        }
    }

    /** Mismo criterio que las categorías: el color de la paleta menos usado. */
    private String siguienteColor() {
        Map<String, Long> usos = metas.listarActivas().stream()
                .collect(Collectors.groupingBy(Meta::color, Collectors.counting()));
        return Categoria.PALETA.stream()
                .min(Comparator.comparingLong(color -> usos.getOrDefault(color, 0L)))
                .orElseThrow();
    }
}
