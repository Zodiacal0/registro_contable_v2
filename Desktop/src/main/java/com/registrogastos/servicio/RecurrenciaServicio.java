package com.registrogastos.servicio;

import com.registrogastos.dominio.Gasto;
import com.registrogastos.dominio.Recurrencia;
import com.registrogastos.persistencia.CategoriaRepositorio;
import com.registrogastos.persistencia.GastoRepositorio;
import com.registrogastos.persistencia.RecurrenciaRepositorio;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Casos de uso de los gastos recurrentes. El corazón es {@link #generarPendientes()}: convierte
 * en gastos normales los cobros que ya vencieron. Se llama al abrir la app y tras cada cambio.
 */
public final class RecurrenciaServicio {

    private final RecurrenciaRepositorio recurrencias;
    private final GastoRepositorio gastos;
    private final CategoriaRepositorio categorias;
    private final Clock reloj;

    public RecurrenciaServicio(RecurrenciaRepositorio recurrencias, GastoRepositorio gastos,
                               CategoriaRepositorio categorias, Clock reloj) {
        this.recurrencias = recurrencias;
        this.gastos = gastos;
        this.categorias = categorias;
        this.reloj = reloj;
    }

    /** Crea la recurrencia y registra de inmediato los cobros que ya vencieron (incluido hoy). */
    public Recurrencia crear(String descripcion, BigDecimal monto, String categoriaId,
                             int cada, Recurrencia.Unidad unidad, LocalDate primerCobro) {
        return crear(descripcion, monto, categoriaId, null, cada, unidad, primerCobro);
    }

    /** @param cuentaId de qué cuenta salen los cobros; null = la principal */
    public Recurrencia crear(String descripcion, BigDecimal monto, String categoriaId, String cuentaId,
                             int cada, Recurrencia.Unidad unidad, LocalDate primerCobro) {
        validarCategoria(categoriaId);
        var nueva = Recurrencia.nueva(descripcion, monto, categoriaId, cuentaId, cada, unidad, primerCobro,
                reloj.instant());
        recurrencias.guardar(nueva);
        generarPendientes();
        return buscar(nueva.id());
    }

    /** Cambia los cobros futuros; los ya registrados quedan como estaban. */
    public Recurrencia editar(String id, String descripcion, BigDecimal monto, String categoriaId,
                              int cada, Recurrencia.Unidad unidad) {
        return editar(id, descripcion, monto, categoriaId, cada, unidad, null);
    }

    /**
     * Como {@link #editar(String, String, BigDecimal, String, int, Recurrencia.Unidad)}, pero
     * además puede mover el primer cobro. Si {@code inicio} cambia, se rehace el calendario:
     * <ul>
     *   <li>los cobros registrados que caen en el calendario nuevo se conservan;</li>
     *   <li>los que ya no caen se eliminan (borrado lógico);</li>
     *   <li>se registran los que falten desde el nuevo inicio hasta hoy.</li>
     * </ul>
     * Como el id de cada cobro sale de "recurrencia|fecha", regenerar nunca duplica.
     *
     * @param inicio nuevo primer cobro; null = el mismo
     */
    public Recurrencia editar(String id, String descripcion, BigDecimal monto, String categoriaId,
                              int cada, Recurrencia.Unidad unidad, LocalDate inicio) {
        validarCategoria(categoriaId);
        var actual = buscar(id);
        var editada = actual.editar(descripcion, monto, categoriaId, cada, unidad, reloj.instant());
        if (inicio != null && !inicio.equals(actual.inicio())) {
            editada = editada.conInicio(inicio, reloj.instant());
            for (Gasto cobro : gastos.listarPorRecurrencia(id)) {
                if (!editada.esCobro(cobro.fecha())) {
                    gastos.guardar(cobro.eliminar(reloj.instant()));
                }
            }
            recurrencias.guardar(editada);
            generarPendientes();
            return buscar(id);
        }
        recurrencias.guardar(editada);
        return editada;
    }

    public void pausar(String id) {
        recurrencias.guardar(buscar(id).pausar(reloj.instant()));
    }

    public void reanudar(String id) {
        recurrencias.guardar(buscar(id).reanudar(LocalDate.now(reloj), reloj.instant()));
        generarPendientes();
    }

    /** Borrado lógico. Los cobros que ya generó se conservan: fueron gastos reales. */
    public void eliminar(String id) {
        recurrencias.guardar(buscar(id).eliminar(reloj.instant()));
    }

    public List<Recurrencia> listar() {
        return recurrencias.listar();
    }

    /**
     * Registra como gastos los cobros vencidos (fecha <= hoy) de cada recurrencia activa.
     * Es idempotente: correrlo dos veces no duplica, porque cada cobro tiene un id fijo y se
     * inserta con "si no existe".
     *
     * @return cuántos gastos nuevos se registraron
     */
    public int generarPendientes() {
        LocalDate hoy = LocalDate.now(reloj);
        int nuevos = 0;
        for (Recurrencia r : recurrencias.listar()) {
            if (!r.activa()) {
                continue;
            }
            for (LocalDate fecha : r.cobrosEntre(r.generadaHasta(), hoy)) {
                if (gastos.insertarSiNoExiste(Gasto.deRecurrencia(r, fecha, reloj.instant()))) {
                    nuevos++;
                }
            }
            if (r.generadaHasta() == null || r.generadaHasta().isBefore(hoy)) {
                recurrencias.guardar(r.generadaHasta(hoy, reloj.instant()));
            }
        }
        return nuevos;
    }

    /** Cobros de las recurrencias activas desde mañana hasta dentro de {@code dias} días. */
    public List<ProximoCobro> proximos(int dias) {
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate hasta = hoy.plusDays(dias);
        return recurrencias.listar().stream()
                .filter(Recurrencia::activa)
                .flatMap(r -> r.cobrosEntre(hoy, hasta).stream().map(f -> new ProximoCobro(r, f)))
                .sorted(Comparator.comparing(ProximoCobro::fecha))
                .toList();
    }

    /** Lo que suman por mes las recurrencias activas (aproximado para las que no son mensuales). */
    public BigDecimal costoMensual() {
        return costoMensual(Conversor.SOLO_PRINCIPAL);
    }

    /** En moneda principal: cada recurrencia se convierte según la moneda de su cuenta. */
    public BigDecimal costoMensual(Conversor conv) {
        return recurrencias.listar().stream()
                .filter(Recurrencia::activa)
                .map(r -> conv.aPrincipal(r.costoMensual(), conv.monedaDe(r.cuentaId())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<Gasto> cobrosDe(String id) {
        return gastos.listarPorRecurrencia(id);
    }

    private Recurrencia buscar(String id) {
        return recurrencias.buscarPorId(id)
                .filter(r -> !r.eliminado())
                .orElseThrow(() -> new NoSuchElementException("No existe la recurrencia " + id));
    }

    private void validarCategoria(String categoriaId) {
        if (categoriaId != null && categorias.buscarPorId(categoriaId).filter(c -> !c.eliminado()).isEmpty()) {
            throw new IllegalArgumentException("La categoría no existe o fue eliminada");
        }
    }
}
