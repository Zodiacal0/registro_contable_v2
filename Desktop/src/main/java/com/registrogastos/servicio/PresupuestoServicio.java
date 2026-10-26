package com.registrogastos.servicio;

import com.registrogastos.dominio.Gasto;
import com.registrogastos.dominio.LineaPlan;
import com.registrogastos.dominio.Recurrencia;
import com.registrogastos.persistencia.AjustesRepositorio;
import com.registrogastos.persistencia.IngresoRepositorio;
import com.registrogastos.persistencia.LineaPlanRepositorio;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Casos de uso del plan mensual: el sueldo, las líneas del plan y la foto {@link PlanMes}. */
public final class PresupuestoServicio {

    private static final String CLAVE_INGRESO = "ingreso_mensual";
    private static final String CLAVE_QUINCENAL = "pago_quincenal";

    private final LineaPlanRepositorio lineas;
    private final AjustesRepositorio ajustes;
    private final IngresoRepositorio ingresos;
    private final GastoServicio gastos;
    private final RecurrenciaServicio recurrencias;
    private final Supplier<Conversor> conversor;
    private final Clock reloj;

    public PresupuestoServicio(LineaPlanRepositorio lineas, AjustesRepositorio ajustes, IngresoRepositorio ingresos,
                               GastoServicio gastos, RecurrenciaServicio recurrencias, Supplier<Conversor> conversor,
                               Clock reloj) {
        this.lineas = lineas;
        this.ajustes = ajustes;
        this.ingresos = ingresos;
        this.gastos = gastos;
        this.recurrencias = recurrencias;
        this.conversor = conversor;
        this.reloj = reloj;
    }

    // ---- Sueldo ----

    /** El sueldo esperado al mes; vacío = usar lo que se registre en Cuentas. */
    public Optional<BigDecimal> ingresoMensual() {
        return ajustes.leer(CLAVE_INGRESO).filter(v -> !v.isBlank()).map(BigDecimal::new);
    }

    public void fijarIngresoMensual(BigDecimal monto) {
        if (monto != null && monto.signum() < 0) {
            throw new IllegalArgumentException("El ingreso no puede ser negativo");
        }
        ajustes.guardar(CLAVE_INGRESO, monto == null || monto.signum() == 0 ? "" : monto.toPlainString(), reloj.instant());
    }

    public boolean quincenal() {
        return ajustes.leer(CLAVE_QUINCENAL).map("1"::equals).orElse(false);
    }

    public void fijarQuincenal(boolean quincenal) {
        ajustes.guardar(CLAVE_QUINCENAL, quincenal ? "1" : "0", reloj.instant());
    }

    // ---- Líneas ----

    public LineaPlan agregar(String nombre, BigDecimal monto, LineaPlan.Grupo grupo) {
        var linea = LineaPlan.nueva(nombre, monto, grupo, lineas.ultimoOrden() + 1, reloj.instant());
        lineas.guardar(linea);
        return linea;
    }

    public void editar(String id, String nombre, BigDecimal monto) {
        lineas.guardar(buscar(id).editar(nombre, monto, reloj.instant()));
    }

    public void eliminar(String id) {
        lineas.guardar(buscar(id).eliminar(reloj.instant()));
    }

    public void cambiarQuincena(String id, LineaPlan.Quincena quincena) {
        lineas.guardar(buscar(id).conQuincena(quincena, reloj.instant()));
    }

    /**
     * Crea una línea de gasto por cada recurrencia activa que aún no esté en el plan (se
     * compara por nombre, sin tildes ni mayúsculas), con su costo mensual en moneda principal.
     *
     * @return cuántas se agregaron
     */
    public int traerRecurrentes() {
        int agregadas = 0;
        for (Recurrencia r : sugeridas()) {
            Conversor conv = conversor.get();
            agregar(r.descripcion(), conv.aPrincipal(r.costoMensual(), conv.monedaDe(r.cuentaId())), LineaPlan.Grupo.GASTO);
            agregadas++;
        }
        return agregadas;
    }

    // ---- El plan ----

    public PlanMes plan(YearMonth mes) {
        Conversor conv = conversor.get();
        BigDecimal registrado = ingresos.listarEntre(mes.atDay(1), mes.atEndOfMonth()).stream()
                .map(i -> conv.aPrincipal(i.monto(), conv.monedaDe(i.cuentaId())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Optional<BigDecimal> fijado = ingresoMensual();
        List<LineaPlan> todas = lineas.listarActivas();
        List<Gasto> delMes = gastos.delMes(mes);
        return new PlanMes(mes, fijado.orElse(registrado), fijado.isPresent(), registrado,
                todas.stream().filter(l -> l.grupo() == LineaPlan.Grupo.GASTO).toList(),
                todas.stream().filter(l -> l.grupo() == LineaPlan.Grupo.ASIGNACION).toList(),
                gastos.total(delMes),
                gastos.total(delMes.stream().filter(g -> g.fecha().getDayOfMonth() <= 15).toList()),
                sugeridas(),
                quincenal());
    }

    private List<Recurrencia> sugeridas() {
        Set<String> enPlan = lineas.listarActivas().stream().map(l -> clave(l.nombre())).collect(Collectors.toSet());
        return recurrencias.listar().stream()
                .filter(Recurrencia::activa)
                .filter(r -> !enPlan.contains(clave(r.descripcion())))
                .toList();
    }

    private static String clave(String texto) {
        return Normalizer.normalize(texto.strip().toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private LineaPlan buscar(String id) {
        return lineas.buscarPorId(id)
                .filter(l -> !l.eliminado())
                .orElseThrow(() -> new NoSuchElementException("No existe la línea " + id));
    }
}
