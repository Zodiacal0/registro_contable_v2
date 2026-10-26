package com.registrogastos.ui;

import com.registrogastos.dominio.Abono;
import com.registrogastos.dominio.Categoria;
import com.registrogastos.dominio.Cuenta;
import com.registrogastos.dominio.Deuda;
import com.registrogastos.dominio.Gasto;
import com.registrogastos.dominio.LineaPlan;
import com.registrogastos.dominio.Meta;
import com.registrogastos.dominio.Moneda;
import com.registrogastos.dominio.Recurrencia;
import com.registrogastos.servicio.Analisis;
import com.registrogastos.servicio.AnaliticaServicio;
import com.registrogastos.servicio.Conversor;
import com.registrogastos.servicio.CuentaServicio;
import com.registrogastos.servicio.DeudaServicio;
import com.registrogastos.servicio.EstadoCuenta;
import com.registrogastos.servicio.EstadoDeuda;
import com.registrogastos.servicio.EstadoMeta;
import com.registrogastos.servicio.GastoServicio;
import com.registrogastos.servicio.MetaServicio;
import com.registrogastos.servicio.MovimientoCuenta;
import com.registrogastos.servicio.PlanMes;
import com.registrogastos.servicio.PresupuestoServicio;
import com.registrogastos.servicio.ProximoCobro;
import com.registrogastos.servicio.RecurrenciaServicio;
import com.registrogastos.servicio.ResumenMes;
import com.registrogastos.servicio.Servicios;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Estado observable de la UI. Las vistas escuchan {@link #resumenProperty()} y se
 * repintan; nunca llaman al servicio directamente.
 *
 * Flujo: acción → servicio → recargar() → nuevo ResumenMes → listeners → vistas repintan.
 */
public final class ModeloUi {

    private final GastoServicio servicio;
    private final RecurrenciaServicio recurrencias;
    private final MetaServicio metas;
    private final DeudaServicio deudas;
    private final CuentaServicio cuentas;
    private final AnaliticaServicio analitica;
    private final PresupuestoServicio presupuestos;
    private final ObjectProperty<YearMonth> mes;
    // Se leen junto con el resumen en recargar(): una sola señal de "cambiaron los datos"
    private List<Recurrencia> listaRecurrencias = List.of();
    private List<EstadoMeta> listaMetas = List.of();
    private List<EstadoDeuda> listaDeudas = List.of();
    private List<EstadoCuenta> listaCuentas = List.of();
    private PlanMes plan;
    // Wrapper: dentro se escribe, hacia afuera solo se expone la versión de lectura
    private final ReadOnlyObjectWrapper<ResumenMes> resumen = new ReadOnlyObjectWrapper<>();
    /** Gasto cargado en el panel para editar; null = el panel está en modo "nuevo". */
    private final ObjectProperty<Gasto> seleccionado = new SimpleObjectProperty<>();

    // Filtros de la tabla de movimientos. null = sin filtro.
    private final ObjectProperty<LocalDate> diaFiltro = new SimpleObjectProperty<>();
    /** Id de categoría a mostrar; {@link #SIN_CATEGORIA} = solo los gastos sin categoría. */
    private final ObjectProperty<String> categoriaFiltro = new SimpleObjectProperty<>();
    private final StringProperty busqueda = new SimpleStringProperty("");
    public static final String SIN_CATEGORIA = "";

    /** Id del último gasto registrado, para resaltarlo una vez en la tabla. */
    private String ultimoRegistradoId;

    public ModeloUi(Servicios servicios) {
        this.servicio = servicios.gastos();
        this.recurrencias = servicios.recurrencias();
        this.metas = servicios.metas();
        this.deudas = servicios.deudas();
        this.cuentas = servicios.cuentas();
        this.analitica = servicios.analitica();
        this.presupuestos = servicios.presupuestos();
        // Al abrir la app se registran los cobros recurrentes que vencieron desde la última vez
        recurrencias.generarPendientes();
        this.mes = new SimpleObjectProperty<>(servicio.mesActual());
        mes.addListener((obs, anterior, nuevo) -> {
            diaFiltro.set(null); // un día pertenece a un mes: al cambiar de mes deja de aplicar
            recargar();
        });
        recargar();
    }

    public ObjectProperty<LocalDate> diaFiltroProperty() {
        return diaFiltro;
    }

    public ObjectProperty<String> categoriaFiltroProperty() {
        return categoriaFiltro;
    }

    public StringProperty busquedaProperty() {
        return busqueda;
    }

    /** Activa el filtro por día, o lo quita si ya era ese día (clic de nuevo = deshacer). */
    public void alternarDia(LocalDate dia) {
        diaFiltro.set(dia.equals(diaFiltro.get()) ? null : dia);
    }

    public void alternarCategoria(String id) {
        categoriaFiltro.set(Objects.equals(id, categoriaFiltro.get()) ? null : id);
    }

    public boolean hayFiltros() {
        return diaFiltro.get() != null || categoriaFiltro.get() != null || !busqueda.get().isBlank();
    }

    public void quitarFiltros() {
        diaFiltro.set(null);
        categoriaFiltro.set(null);
        busqueda.set("");
    }

    /** Movimientos del mes que pasan los filtros activos. La búsqueda ignora mayúsculas y tildes. */
    public List<Gasto> movimientosFiltrados() {
        String texto = normalizar(busqueda.get());
        return resumen().gastos().stream()
                .filter(g -> diaFiltro.get() == null || g.fecha().equals(diaFiltro.get()))
                .filter(g -> categoriaFiltro.get() == null || claveCategoria(g).equals(categoriaFiltro.get()))
                .filter(g -> texto.isEmpty() || normalizar(g.descripcion()).contains(texto))
                .toList();
    }

    /** Id de la categoría del gasto, o SIN_CATEGORIA si no tiene o la suya fue eliminada. */
    private String claveCategoria(Gasto gasto) {
        return resumen().categoria(gasto.categoriaId()).map(Categoria::id).orElse(SIN_CATEGORIA);
    }

    /** Devuelve el id del gasto recién registrado y lo olvida: el resaltado ocurre una sola vez. */
    public String tomarUltimoRegistrado() {
        String id = ultimoRegistradoId;
        ultimoRegistradoId = null;
        return id;
    }

    /** "Café" -> "cafe": quita tildes con Normalizer (separa la letra de su acento y borra el acento). */
    private static String normalizar(String texto) {
        return Normalizer.normalize(texto.strip().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    public ReadOnlyObjectProperty<ResumenMes> resumenProperty() {
        return resumen.getReadOnlyProperty();
    }

    public ResumenMes resumen() {
        return resumen.get();
    }

    public ObjectProperty<Gasto> seleccionadoProperty() {
        return seleccionado;
    }

    public LocalDate hoy() {
        return servicio.hoy();
    }

    /** Gastos de hoy, sin importar qué mes se esté mirando (para el panel de captura). */
    public List<Gasto> gastosDeHoy() {
        LocalDate hoy = hoy();
        return servicio.delMes(YearMonth.from(hoy)).stream().filter(g -> g.fecha().equals(hoy)).toList();
    }

    public boolean enMesActual() {
        return mes.get().equals(servicio.mesActual());
    }

    public void mesAnterior() {
        mes.set(mes.get().minusMonths(1));
    }

    public void mesSiguiente() {
        mes.set(mes.get().plusMonths(1));
    }

    public void irAMesActual() {
        mes.set(servicio.mesActual());
    }

    public void registrar(String descripcion, BigDecimal monto, LocalDate fecha, Categoria categoria, String cuentaId) {
        ultimoRegistradoId = servicio.registrar(descripcion, monto, fecha, idDe(categoria), cuentaId).id();
        mostrarMesDe(fecha);
    }

    public void editar(String id, String descripcion, BigDecimal monto, LocalDate fecha, Categoria categoria,
                       String cuentaId) {
        servicio.editar(id, descripcion, monto, fecha, idDe(categoria), cuentaId);
        seleccionado.set(null);
        mostrarMesDe(fecha);
    }

    public void eliminar(String id) {
        servicio.eliminar(id);
        seleccionado.set(null);
        recargar();
    }

    public void crearCategoria(String nombre) {
        servicio.crearCategoria(nombre);
        recargar();
    }

    public void renombrarCategoria(String id, String nombre) {
        servicio.renombrarCategoria(id, nombre);
        recargar();
    }

    public void eliminarCategoria(String id) {
        servicio.eliminarCategoria(id);
        recargar();
    }

    /** Tras guardar, se muestra el mes del gasto para que el usuario lo vea aparecer. */
    private void mostrarMesDe(LocalDate fecha) {
        var mesDelGasto = YearMonth.from(fecha);
        if (mesDelGasto.equals(mes.get())) {
            recargar();
        } else {
            mes.set(mesDelGasto); // el listener de mes recarga
        }
    }

    /**
     * Una sola señal de "cambiaron los datos": las recurrencias se leen ANTES de publicar el
     * resumen, así cualquier vista que escuche el resumen ya las encuentra actualizadas.
     */
    private void recargar() {
        listaCuentas = cuentas.listar();
        plan = presupuestos.plan(mes.get());
        listaRecurrencias = recurrencias.listar();
        listaMetas = metas.listar();
        listaDeudas = deudas.listar();
        resumen.set(servicio.resumen(mes.get()));
    }

    // ---- Metas de ahorro ----

    public List<EstadoMeta> metas() {
        return listaMetas;
    }

    public List<Abono> aportesDe(String metaId) {
        return metas.aportesDe(metaId);
    }

    public Meta crearMeta(String nombre, BigDecimal objetivo, LocalDate fechaObjetivo) {
        Meta meta = metas.crear(nombre, objetivo, fechaObjetivo);
        recargar();
        return meta;
    }

    public void editarMeta(String id, String nombre, BigDecimal objetivo, LocalDate fechaObjetivo) {
        metas.editar(id, nombre, objetivo, fechaObjetivo);
        recargar();
    }

    public void eliminarMeta(String id) {
        metas.eliminar(id);
        recargar();
    }

    public void aportar(String metaId, BigDecimal monto, LocalDate fecha) {
        metas.aportar(metaId, monto, fecha);
        recargar();
    }

    public void retirar(String metaId, BigDecimal monto, LocalDate fecha) {
        metas.retirar(metaId, monto, fecha);
        recargar();
    }

    public void eliminarAporte(String aporteId) {
        metas.eliminarAporte(aporteId);
        recargar();
    }

    // ---- Deudas ----

    public List<EstadoDeuda> deudas() {
        return listaDeudas;
    }

    public List<Abono> abonosDe(String deudaId) {
        return deudas.abonosDe(deudaId);
    }

    public Deuda crearDeuda(String nombre, Deuda.Tipo tipo, BigDecimal monto, LocalDate fecha, LocalDate fechaLimite) {
        Deuda deuda = deudas.crear(nombre, tipo, monto, fecha, fechaLimite);
        recargar();
        return deuda;
    }

    public void editarDeuda(String id, String nombre, BigDecimal monto, LocalDate fechaLimite) {
        deudas.editar(id, nombre, monto, fechaLimite);
        recargar();
    }

    public void eliminarDeuda(String id) {
        deudas.eliminar(id);
        recargar();
    }

    /**
     * Si es una deuda que debo, el abono es un gasto: se registra, se muestra su mes y se
     * resalta en Movimientos como cualquier gasto recién registrado.
     */
    public void abonarDeuda(String deudaId, BigDecimal monto, LocalDate fecha, Categoria categoria, String descripcion,
                            String cuentaId) {
        ultimoRegistradoId = deudas.abonar(deudaId, monto, fecha, idDe(categoria), descripcion, cuentaId);
        mostrarMesDe(fecha);
    }

    /** Saldo actual de una deuda, ya recargado (para confirmar tras abonar). */
    public Optional<EstadoDeuda> deuda(String id) {
        return listaDeudas.stream().filter(d -> d.deuda().id().equals(id)).findFirst();
    }

    public Optional<EstadoMeta> meta(String id) {
        return listaMetas.stream().filter(m -> m.meta().id().equals(id)).findFirst();
    }

    public void eliminarAbonoDeuda(String deudaId, String abonoId) {
        deudas.eliminarAbono(deudaId, abonoId);
        recargar();
    }

    // ---- Recurrencias ----

    public List<Recurrencia> recurrencias() {
        return listaRecurrencias;
    }

    public List<ProximoCobro> proximosCobros(int dias) {
        return recurrencias.proximos(dias);
    }

    public BigDecimal costoMensualRecurrente() {
        return recurrencias.costoMensual(resumen().conversor());
    }

    public List<Gasto> cobrosDe(String recurrenciaId) {
        return recurrencias.cobrosDe(recurrenciaId);
    }

    public Optional<Recurrencia> recurrencia(String id) {
        return listaRecurrencias.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    public void crearRecurrencia(String descripcion, BigDecimal monto, LocalDate primerCobro,
                                 Categoria categoria, String cuentaId, int cada, Recurrencia.Unidad unidad) {
        recurrencias.crear(descripcion, monto, idDe(categoria), cuentaId, cada, unidad, primerCobro);
        mostrarMesDe(primerCobro.isAfter(hoy()) ? hoy() : primerCobro);
    }

    /** @param inicio nuevo primer cobro (si cambia, se rehace el historial de cobros) */
    public void editarRecurrencia(String id, String descripcion, BigDecimal monto, Categoria categoria,
                                  int cada, Recurrencia.Unidad unidad, LocalDate inicio) {
        recurrencias.editar(id, descripcion, monto, idDe(categoria), cada, unidad, inicio);
        recargar();
    }

    public void pausarRecurrencia(String id) {
        recurrencias.pausar(id);
        recargar();
    }

    public void reanudarRecurrencia(String id) {
        recurrencias.reanudar(id);
        recargar();
    }

    public void eliminarRecurrencia(String id) {
        recurrencias.eliminar(id);
        recargar();
    }

    // ---- Monedas ----

    public Conversor conversor() {
        return resumen().conversor();
    }

    public Moneda monedaDe(String cuentaId) {
        return conversor().monedaDe(cuentaId);
    }

    /** El monto de un gasto en su moneda: "12.00" si es la principal, "US$ 12.00" si no. */
    public String monto(Gasto gasto) {
        return Formato.monto(gasto.monto(), monedaDe(gasto.cuentaId()));
    }

    public String monto(Recurrencia recurrencia) {
        return Formato.monto(recurrencia.monto(), monedaDe(recurrencia.cuentaId()));
    }

    /** Suma en moneda principal. */
    public BigDecimal total(List<Gasto> gastos) {
        return gastos.stream().map(conversor()::aPrincipal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal tipoCambio(Moneda moneda) {
        return cuentas.tipoCambio(moneda);
    }

    public void fijarTipoCambio(Moneda moneda, BigDecimal valor) {
        cuentas.fijarTipoCambio(moneda, valor);
        recargar(); // los totales convertidos cambian
    }

    // ---- Cuentas ----

    public List<EstadoCuenta> cuentas() {
        return listaCuentas;
    }

    public Optional<EstadoCuenta> cuenta(String id) {
        return listaCuentas.stream().filter(c -> c.cuenta().id().equals(id)).findFirst();
    }

    /** Suma de todos los saldos en moneda principal. */
    public BigDecimal patrimonio() {
        return listaCuentas.stream().map(EstadoCuenta::saldoPrincipal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<MovimientoCuenta> movimientosDe(String cuentaId) {
        return cuentas.movimientos(cuentaId);
    }

    public Cuenta crearCuenta(String nombre, Cuenta.Tipo tipo, Moneda moneda, BigDecimal saldoInicial) {
        Cuenta cuenta = cuentas.crear(nombre, tipo, moneda, saldoInicial);
        recargar();
        return cuenta;
    }

    public void editarCuenta(String id, String nombre, Cuenta.Tipo tipo, BigDecimal saldoInicial) {
        cuentas.editar(id, nombre, tipo, saldoInicial);
        recargar();
    }

    public void eliminarCuenta(String id) {
        cuentas.eliminar(id);
        recargar();
    }

    public void registrarIngreso(String cuentaId, String descripcion, BigDecimal monto, LocalDate fecha) {
        cuentas.registrarIngreso(cuentaId, descripcion, monto, fecha);
        recargar();
    }

    public void transferir(String origenId, String destinoId, BigDecimal salida, BigDecimal entrada, LocalDate fecha) {
        cuentas.transferir(origenId, destinoId, salida, entrada, fecha);
        recargar();
    }

    /** Quita un ingreso o una transferencia desde el historial de la cuenta. */
    public void quitarMovimientoCuenta(MovimientoCuenta movimiento) {
        switch (movimiento.tipo()) {
            case INGRESO -> cuentas.eliminarIngreso(movimiento.id());
            case ENTRADA, SALIDA -> cuentas.eliminarTransferencia(movimiento.id());
            case GASTO -> throw new IllegalArgumentException("Los gastos se eliminan desde Movimientos");
        }
        recargar();
    }

    // ---- Presupuesto ----

    /** El plan del mes que se está viendo, leído junto con el resumen. */
    public PlanMes plan() {
        return plan;
    }

    public Optional<BigDecimal> ingresoMensual() {
        return presupuestos.ingresoMensual();
    }

    /** @param monto null o cero = usar lo registrado en Cuentas */
    public void fijarIngresoMensual(BigDecimal monto) {
        presupuestos.fijarIngresoMensual(monto);
        recargar();
    }

    public void agregarLineaPlan(String nombre, BigDecimal monto, LineaPlan.Grupo grupo) {
        presupuestos.agregar(nombre, monto, grupo);
        recargar();
    }

    public void editarLineaPlan(String id, String nombre, BigDecimal monto) {
        presupuestos.editar(id, nombre, monto);
        recargar();
    }

    public void eliminarLineaPlan(String id) {
        presupuestos.eliminar(id);
        recargar();
    }

    public void traerRecurrentesAlPlan() {
        presupuestos.traerRecurrentes();
        recargar();
    }

    public void fijarPagoQuincenal(boolean quincenal) {
        presupuestos.fijarQuincenal(quincenal);
        recargar();
    }

    public void cambiarQuincenaLineaPlan(String id, LineaPlan.Quincena quincena) {
        presupuestos.cambiarQuincena(id, quincena);
        recargar();
    }

    // ---- Analíticas ----

    /** Lee y calcula; no toca propiedades de JavaFX, así que puede correr en otro hilo. */
    public Analisis analizar() {
        return analitica.analizar();
    }

    private static String idDe(Categoria categoria) {
        return categoria == null ? null : categoria.id();
    }
}
