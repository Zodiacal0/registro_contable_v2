package com.registrogastos.servicio;

import com.registrogastos.dominio.Cuenta;
import com.registrogastos.dominio.Gasto;
import com.registrogastos.dominio.Ingreso;
import com.registrogastos.dominio.Moneda;
import com.registrogastos.dominio.Transferencia;
import com.registrogastos.persistencia.AjustesRepositorio;
import com.registrogastos.persistencia.CuentaRepositorio;
import com.registrogastos.persistencia.GastoRepositorio;
import com.registrogastos.persistencia.IngresoRepositorio;
import com.registrogastos.persistencia.TransferenciaRepositorio;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Casos de uso de las cuentas. El saldo nunca se guarda; se calcula:
 * saldo inicial + ingresos - gastos + transferencias que entran - transferencias que salen.
 */
public final class CuentaServicio {

    private static final String CLAVE_CAMBIO = "tipo_cambio_";

    private final CuentaRepositorio cuentas;
    private final IngresoRepositorio ingresos;
    private final TransferenciaRepositorio transferencias;
    private final GastoRepositorio gastos;
    private final AjustesRepositorio ajustes;
    private final Clock reloj;

    public CuentaServicio(CuentaRepositorio cuentas, IngresoRepositorio ingresos, TransferenciaRepositorio transferencias,
                          GastoRepositorio gastos, AjustesRepositorio ajustes, Clock reloj) {
        this.cuentas = cuentas;
        this.ingresos = ingresos;
        this.transferencias = transferencias;
        this.gastos = gastos;
        this.ajustes = ajustes;
        this.reloj = reloj;
    }

    // ---- Cuentas ----

    public Cuenta crear(String nombre, Cuenta.Tipo tipo, Moneda moneda, BigDecimal saldoInicial) {
        var cuenta = Cuenta.nueva(nombre, tipo, moneda, saldoInicial, reloj.instant());
        cuentas.guardar(cuenta);
        return cuenta;
    }

    public void editar(String id, String nombre, Cuenta.Tipo tipo, BigDecimal saldoInicial) {
        cuentas.guardar(buscar(id).editar(nombre, tipo, saldoInicial, reloj.instant()));
    }

    /**
     * Borrado lógico. Sus gastos se conservan (el dinero sí salió) y siguen sumando en el
     * resumen; la cuenta solo deja de ofrecerse para registrar.
     */
    public void eliminar(String id) {
        if (Cuenta.PRINCIPAL_ID.equals(id)) {
            throw new IllegalArgumentException("La cuenta principal no se puede eliminar");
        }
        cuentas.guardar(buscar(id).eliminar(reloj.instant()));
    }

    public List<Cuenta> activas() {
        return cuentas.listarActivas();
    }

    /** Cada cuenta activa con su saldo, en el orden de {@link CuentaRepositorio#listarActivas()}. */
    public List<EstadoCuenta> listar() {
        Conversor conversor = conversor();
        return cuentas.listarActivas().stream()
                .map(c -> {
                    BigDecimal saldo = saldo(c);
                    return new EstadoCuenta(c, saldo, conversor.aPrincipal(saldo, c.moneda()));
                })
                .toList();
    }

    // ---- Ingresos y transferencias ----

    public Ingreso registrarIngreso(String cuentaId, String descripcion, BigDecimal monto, LocalDate fecha) {
        buscar(cuentaId);
        var ingreso = Ingreso.nuevo(cuentaId, descripcion, monto, fecha, reloj.instant());
        ingresos.guardar(ingreso);
        return ingreso;
    }

    public void eliminarIngreso(String id) {
        ingresos.buscarPorId(id).ifPresent(i -> ingresos.guardar(i.eliminar(reloj.instant())));
    }

    /**
     * Mueve dinero entre cuentas.
     *
     * @param entrada lo que llega al destino; null = convertir {@code salida} con el tipo de
     *                cambio (igual a salida si ambas cuentas usan la misma moneda)
     */
    public Transferencia transferir(String origenId, String destinoId, BigDecimal salida, BigDecimal entrada,
                                    LocalDate fecha) {
        Cuenta origen = buscar(origenId);
        Cuenta destino = buscar(destinoId);
        BigDecimal llega = entrada != null ? entrada : conversor().convertir(salida, origen.moneda(), destino.moneda());
        var transferencia = Transferencia.nueva(origenId, destinoId, salida, llega, fecha, reloj.instant());
        transferencias.guardar(transferencia);
        return transferencia;
    }

    public void eliminarTransferencia(String id) {
        transferencias.buscarPorId(id).ifPresent(t -> transferencias.guardar(t.eliminar(reloj.instant())));
    }

    /** Historial de una cuenta: gastos, ingresos y transferencias juntos, lo más reciente primero. */
    public List<MovimientoCuenta> movimientos(String cuentaId) {
        var nombres = cuentas.listarTodas().stream().collect(Collectors.toMap(Cuenta::id, Cuenta::nombre));
        var lista = new ArrayList<MovimientoCuenta>();
        for (Gasto g : gastos.listarPorCuenta(cuentaId)) {
            lista.add(new MovimientoCuenta(g.id(), MovimientoCuenta.Tipo.GASTO, g.descripcion(),
                    g.monto().negate(), g.fecha(), g.creadoEn()));
        }
        for (Ingreso i : ingresos.listarDe(cuentaId)) {
            lista.add(new MovimientoCuenta(i.id(), MovimientoCuenta.Tipo.INGRESO, i.descripcion(),
                    i.monto(), i.fecha(), i.creadoEn()));
        }
        for (Transferencia t : transferencias.listarDe(cuentaId)) {
            boolean sale = t.origenId().equals(cuentaId);
            lista.add(sale
                    ? new MovimientoCuenta(t.id(), MovimientoCuenta.Tipo.SALIDA,
                            "A " + nombres.getOrDefault(t.destinoId(), "otra cuenta"), t.salida().negate(), t.fecha(), t.creadoEn())
                    : new MovimientoCuenta(t.id(), MovimientoCuenta.Tipo.ENTRADA,
                            "De " + nombres.getOrDefault(t.origenId(), "otra cuenta"), t.entrada(), t.fecha(), t.creadoEn()));
        }
        lista.sort(Comparator.comparing(MovimientoCuenta::fecha).thenComparing(MovimientoCuenta::creadoEn).reversed());
        return lista;
    }

    // ---- Tipo de cambio ----

    /** Cuántos quetzales vale 1 unidad de la moneda (1 para la principal). */
    public BigDecimal tipoCambio(Moneda moneda) {
        if (moneda.esPrincipal()) {
            return BigDecimal.ONE;
        }
        return ajustes.leer(CLAVE_CAMBIO + moneda.name()).map(BigDecimal::new).orElse(BigDecimal.ONE);
    }

    public void fijarTipoCambio(Moneda moneda, BigDecimal valor) {
        if (moneda.esPrincipal()) {
            throw new IllegalArgumentException("La moneda principal no tiene tipo de cambio");
        }
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("El tipo de cambio debe ser mayor que cero");
        }
        if (valor.stripTrailingZeros().scale() > 4) {
            throw new IllegalArgumentException("El tipo de cambio admite máximo 4 decimales");
        }
        ajustes.guardar(CLAVE_CAMBIO + moneda.name(), valor.stripTrailingZeros().toPlainString(), reloj.instant());
    }

    /** Foto actual de monedas y tipos de cambio; incluye cuentas eliminadas (sus gastos siguen). */
    public Conversor conversor() {
        var tasas = new EnumMap<Moneda, BigDecimal>(Moneda.class);
        for (Moneda m : Moneda.values()) {
            tasas.put(m, tipoCambio(m));
        }
        return new Conversor(cuentas.listarTodas().stream().collect(Collectors.toMap(Cuenta::id, Cuenta::moneda)), tasas);
    }

    // ---- Internos ----

    private BigDecimal saldo(Cuenta cuenta) {
        BigDecimal saldo = cuenta.saldoInicial();
        for (Ingreso i : ingresos.listarDe(cuenta.id())) {
            saldo = saldo.add(i.monto());
        }
        for (Gasto g : gastos.listarPorCuenta(cuenta.id())) {
            saldo = saldo.subtract(g.monto());
        }
        for (Transferencia t : transferencias.listarDe(cuenta.id())) {
            saldo = t.origenId().equals(cuenta.id()) ? saldo.subtract(t.salida()) : saldo.add(t.entrada());
        }
        return saldo;
    }

    private Cuenta buscar(String id) {
        return cuentas.buscarPorId(id)
                .filter(c -> !c.eliminado())
                .orElseThrow(() -> new NoSuchElementException("No existe la cuenta " + id));
    }
}
