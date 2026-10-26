package com.registrogastos.servicio;

import com.registrogastos.dominio.Abono;
import com.registrogastos.dominio.Deuda;
import com.registrogastos.dominio.Gasto;
import com.registrogastos.persistencia.AbonoRepositorio;
import com.registrogastos.persistencia.CategoriaRepositorio;
import com.registrogastos.persistencia.DeudaRepositorio;
import com.registrogastos.persistencia.GastoRepositorio;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Casos de uso de las deudas. Lo pagado sale de dos lugares según el tipo:
 * - DEBO: los gastos con deuda_id (un abono ES un gasto; borrarlo devuelve el saldo).
 * - ME_DEBEN: la tabla cobros_deuda (lo que me pagan no es un gasto).
 */
public final class DeudaServicio {

    private final DeudaRepositorio deudas;
    private final AbonoRepositorio cobros;
    private final GastoRepositorio gastos;
    private final CategoriaRepositorio categorias;
    private final Clock reloj;

    public DeudaServicio(DeudaRepositorio deudas, AbonoRepositorio cobros, GastoRepositorio gastos,
                         CategoriaRepositorio categorias, Clock reloj) {
        this.deudas = deudas;
        this.cobros = cobros;
        this.gastos = gastos;
        this.categorias = categorias;
        this.reloj = reloj;
    }

    public Deuda crear(String nombre, Deuda.Tipo tipo, BigDecimal monto, LocalDate fecha, LocalDate fechaLimite) {
        var deuda = Deuda.nueva(nombre, tipo, monto, fecha, fechaLimite, reloj.instant());
        deudas.guardar(deuda);
        return deuda;
    }

    public void editar(String id, String nombre, BigDecimal monto, LocalDate fechaLimite) {
        var deuda = buscar(id);
        BigDecimal pagado = pagado(deuda);
        if (monto.compareTo(pagado) < 0) {
            throw new IllegalArgumentException("El monto no puede ser menor a lo ya pagado (" + pagado + ")");
        }
        deudas.guardar(deuda.editar(nombre, monto, fechaLimite, reloj.instant()));
    }

    /** Borrado lógico. Los abonos que eran gastos se conservan: el dinero sí salió. */
    public void eliminar(String id) {
        deudas.guardar(buscar(id).eliminar(reloj.instant()));
    }

    public String abonar(String deudaId, BigDecimal monto, LocalDate fecha, String categoriaId) {
        return abonar(deudaId, monto, fecha, categoriaId, null, null);
    }

    /**
     * Registra un abono. Si debo, se guarda como gasto (aparece en Movimientos); si me deben,
     * como cobro. No se permite abonar más que el saldo pendiente.
     *
     * @param categoriaId solo para DEBO: la categoría del gasto (puede ser null)
     * @param descripcion solo para DEBO: la del gasto; null o vacía = "Abono · nombre"
     * @param cuentaId    solo para DEBO: de qué cuenta sale el abono; null = la principal
     * @return el id del registro creado (el gasto o el cobro)
     */
    public String abonar(String deudaId, BigDecimal monto, LocalDate fecha, String categoriaId,
                         String descripcion, String cuentaId) {
        var deuda = buscar(deudaId);
        BigDecimal saldo = new EstadoDeuda(deuda, pagado(deuda)).saldo();
        if (monto.compareTo(saldo) > 0) {
            throw new IllegalArgumentException("El abono supera el saldo pendiente (" + saldo + ")");
        }
        if (deuda.tipo() == Deuda.Tipo.DEBO) {
            if (categoriaId != null && categorias.buscarPorId(categoriaId).filter(c -> !c.eliminado()).isEmpty()) {
                throw new IllegalArgumentException("La categoría no existe o fue eliminada");
            }
            var gasto = Gasto.abono(deuda, descripcion, monto, fecha, categoriaId, cuentaId, reloj.instant());
            gastos.guardar(gasto);
            return gasto.id();
        }
        var cobro = Abono.nuevo(deudaId, monto, fecha, reloj.instant());
        cobros.guardar(cobro);
        return cobro.id();
    }

    /** Deshace un abono: si era un gasto, se elimina el gasto; si era un cobro, el cobro. */
    public void eliminarAbono(String deudaId, String abonoId) {
        if (buscar(deudaId).tipo() == Deuda.Tipo.DEBO) {
            gastos.buscarPorId(abonoId).ifPresent(g -> gastos.guardar(g.eliminar(reloj.instant())));
        } else {
            cobros.buscarPorId(abonoId).ifPresent(c -> cobros.guardar(c.eliminar(reloj.instant())));
        }
    }

    /** Pendientes primero (la de menor saldo arriba: es la más cerca de saldarse), saldadas al final. */
    public List<EstadoDeuda> listar() {
        return deudas.listarActivas().stream()
                .map(d -> new EstadoDeuda(d, pagado(d)))
                .sorted(Comparator.comparing(EstadoDeuda::saldada).thenComparing(EstadoDeuda::saldo))
                .toList();
    }

    /**
     * Los abonos de una deuda en un solo formato. Para DEBO vienen de gastos y se convierten
     * a Abono; así la UI no necesita saber de dónde salió cada uno.
     */
    public List<Abono> abonosDe(String deudaId) {
        if (buscar(deudaId).tipo() == Deuda.Tipo.ME_DEBEN) {
            return cobros.listarDe(deudaId);
        }
        return gastos.listarPorDeuda(deudaId).stream()
                .map(g -> new Abono(g.id(), deudaId, g.monto(), g.fecha(), g.creadoEn(), g.actualizadoEn(), false))
                .toList();
    }

    private BigDecimal pagado(Deuda deuda) {
        var montos = deuda.tipo() == Deuda.Tipo.DEBO
                ? gastos.listarPorDeuda(deuda.id()).stream().map(Gasto::monto)
                : cobros.listarDe(deuda.id()).stream().map(Abono::monto);
        return montos.reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Deuda buscar(String id) {
        return deudas.buscarPorId(id)
                .filter(d -> !d.eliminado())
                .orElseThrow(() -> new NoSuchElementException("No existe la deuda " + id));
    }
}
