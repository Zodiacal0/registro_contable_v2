package com.registrogastos.ui;

import com.registrogastos.dominio.Categoria;
import com.registrogastos.dominio.Gasto;
import com.registrogastos.dominio.Recurrencia;
import com.registrogastos.servicio.GastoServicio;
import com.registrogastos.servicio.ProximoCobro;
import com.registrogastos.servicio.RecurrenciaServicio;
import com.registrogastos.servicio.ResumenMes;
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
    private final ObjectProperty<YearMonth> mes;
    /** Recurrencias (activas y en pausa); se actualiza junto con el resumen. */
    private List<Recurrencia> listaRecurrencias = List.of();
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

    public ModeloUi(GastoServicio servicio, RecurrenciaServicio recurrencias) {
        this.servicio = servicio;
        this.recurrencias = recurrencias;
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

    public void registrar(String descripcion, BigDecimal monto, LocalDate fecha, Categoria categoria) {
        ultimoRegistradoId = servicio.registrar(descripcion, monto, fecha, idDe(categoria)).id();
        mostrarMesDe(fecha);
    }

    public void editar(String id, String descripcion, BigDecimal monto, LocalDate fecha, Categoria categoria) {
        servicio.editar(id, descripcion, monto, fecha, idDe(categoria));
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
        listaRecurrencias = recurrencias.listar();
        resumen.set(servicio.resumen(mes.get()));
    }

    // ---- Recurrencias ----

    public List<Recurrencia> recurrencias() {
        return listaRecurrencias;
    }

    public List<ProximoCobro> proximosCobros(int dias) {
        return recurrencias.proximos(dias);
    }

    public BigDecimal costoMensualRecurrente() {
        return recurrencias.costoMensual();
    }

    public List<Gasto> cobrosDe(String recurrenciaId) {
        return recurrencias.cobrosDe(recurrenciaId);
    }

    public Optional<Recurrencia> recurrencia(String id) {
        return listaRecurrencias.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    public void crearRecurrencia(String descripcion, BigDecimal monto, LocalDate primerCobro,
                                 Categoria categoria, int cada, Recurrencia.Unidad unidad) {
        recurrencias.crear(descripcion, monto, idDe(categoria), cada, unidad, primerCobro);
        mostrarMesDe(primerCobro.isAfter(hoy()) ? hoy() : primerCobro);
    }

    public void editarRecurrencia(String id, String descripcion, BigDecimal monto, Categoria categoria,
                                  int cada, Recurrencia.Unidad unidad) {
        recurrencias.editar(id, descripcion, monto, idDe(categoria), cada, unidad);
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

    private static String idDe(Categoria categoria) {
        return categoria == null ? null : categoria.id();
    }
}
