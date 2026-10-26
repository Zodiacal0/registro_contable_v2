package com.registrogastos.servicio;

import com.registrogastos.dominio.Categoria;
import com.registrogastos.dominio.Gasto;
import com.registrogastos.persistencia.CategoriaRepositorio;
import com.registrogastos.persistencia.GastoRepositorio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Casos de uso de la app. La UI solo habla con esta clase.
 * El reloj se inyecta para que las pruebas controlen "ahora".
 */
public final class GastoServicio {

    private final GastoRepositorio gastos;
    private final CategoriaRepositorio categorias;
    private final Clock reloj;
    /** Supplier y no Conversor: el tipo de cambio puede cambiar mientras la app corre. */
    private final Supplier<Conversor> conversor;

    public GastoServicio(GastoRepositorio gastos, CategoriaRepositorio categorias, Clock reloj) {
        this(gastos, categorias, () -> Conversor.SOLO_PRINCIPAL, reloj);
    }

    public GastoServicio(GastoRepositorio gastos, CategoriaRepositorio categorias, Supplier<Conversor> conversor,
                         Clock reloj) {
        this.gastos = gastos;
        this.categorias = categorias;
        this.conversor = conversor;
        this.reloj = reloj;
    }

    // ---- Gastos ----

    public Gasto registrar(String descripcion, BigDecimal monto, LocalDate fecha, String categoriaId) {
        return registrar(descripcion, monto, fecha, categoriaId, null);
    }

    /** @param cuentaId de qué cuenta sale; el monto va en la moneda de esa cuenta (null = principal) */
    public Gasto registrar(String descripcion, BigDecimal monto, LocalDate fecha, String categoriaId, String cuentaId) {
        validarCategoria(categoriaId);
        var gasto = Gasto.nuevo(descripcion, monto, fecha, categoriaId, cuentaId, reloj.instant());
        gastos.guardar(gasto);
        return gasto;
    }

    public Gasto editar(String id, String descripcion, BigDecimal monto, LocalDate fecha, String categoriaId) {
        return editar(id, descripcion, monto, fecha, categoriaId, buscarActivo(id).cuentaId());
    }

    public Gasto editar(String id, String descripcion, BigDecimal monto, LocalDate fecha, String categoriaId,
                        String cuentaId) {
        validarCategoria(categoriaId);
        var editado = buscarActivo(id).editar(descripcion, monto, fecha, categoriaId, cuentaId, reloj.instant());
        gastos.guardar(editado);
        return editado;
    }

    public void eliminar(String id) {
        gastos.guardar(buscarActivo(id).eliminar(reloj.instant()));
    }

    public List<Gasto> delMes(YearMonth mes) {
        return gastos.listarEntre(mes.atDay(1), mes.atEndOfMonth());
    }

    /** Suma en moneda principal (cada gasto convertido según su cuenta). */
    public BigDecimal total(List<Gasto> lista) {
        return total(lista, conversor.get());
    }

    public Conversor conversor() {
        return conversor.get();
    }

    public ResumenMes resumen(YearMonth mes) {
        Conversor conv = conversor.get(); // una sola foto para todo el resumen
        List<Gasto> delMes = delMes(mes);
        List<Categoria> activas = categorias();
        BigDecimal total = total(delMes, conv);
        return new ResumenMes(mes, delMes, activas, total, total(delMes(mes.minusMonths(1)), conv),
                diasConsiderados(mes), porCategoria(delMes, activas, total, conv), conv);
    }

    private static BigDecimal total(List<Gasto> lista, Conversor conv) {
        return lista.stream().map(conv::aPrincipal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    public YearMonth mesActual() {
        return YearMonth.now(reloj);
    }

    // ---- Categorías ----

    public Categoria crearCategoria(String nombre) {
        var categoria = Categoria.nueva(nombre, siguienteColor(), reloj.instant());
        categorias.guardar(categoria);
        return categoria;
    }

    public Categoria renombrarCategoria(String id, String nombre) {
        var renombrada = buscarCategoriaActiva(id).renombrar(nombre, reloj.instant());
        categorias.guardar(renombrada);
        return renombrada;
    }

    /** Borrado lógico. Sus gastos se conservan y pasan a mostrarse "sin categoría". */
    public void eliminarCategoria(String id) {
        categorias.guardar(buscarCategoriaActiva(id).eliminar(reloj.instant()));
    }

    public List<Categoria> categorias() {
        return categorias.listarActivas();
    }

    // ---- Internos ----

    /** El color de la paleta menos usado por las categorías activas; ante empate, el primero. */
    private String siguienteColor() {
        Map<String, Long> usos = categorias().stream()
                .collect(Collectors.groupingBy(Categoria::color, Collectors.counting()));
        return Categoria.PALETA.stream()
                .min(Comparator.comparingLong(color -> usos.getOrDefault(color, 0L)))
                .orElseThrow();
    }

    private int diasConsiderados(YearMonth mes) {
        LocalDate hoy = hoy();
        return mes.equals(YearMonth.from(hoy)) ? hoy.getDayOfMonth() : mes.lengthOfMonth();
    }

    static List<TotalCategoria> porCategoria(List<Gasto> gastos, List<Categoria> activas, BigDecimal total,
                                             Conversor conv) {
        Map<String, Categoria> porId = activas.stream()
                .collect(Collectors.toMap(Categoria::id, Function.identity()));

        // groupingBy no acepta claves null: "sin categoría" se representa con Optional.empty()
        Map<Optional<Categoria>, List<Gasto>> grupos = gastos.stream()
                .collect(Collectors.groupingBy(g -> Optional.ofNullable(porId.get(g.categoriaId()))));

        return grupos.entrySet().stream()
                .map(e -> {
                    BigDecimal suma = total(e.getValue(), conv);
                    return new TotalCategoria(e.getKey().orElse(null), suma, e.getValue().size(),
                            suma.divide(total, 4, RoundingMode.HALF_UP).doubleValue());
                })
                .sorted(Comparator.comparing(TotalCategoria::total).reversed())
                .toList();
    }

    private Gasto buscarActivo(String id) {
        return gastos.buscarPorId(id)
                .filter(g -> !g.eliminado())
                .orElseThrow(() -> new NoSuchElementException("No existe el gasto " + id));
    }

    private Categoria buscarCategoriaActiva(String id) {
        return categorias.buscarPorId(id)
                .filter(c -> !c.eliminado())
                .orElseThrow(() -> new NoSuchElementException("No existe la categoría " + id));
    }

    private void validarCategoria(String categoriaId) {
        if (categoriaId == null) {
            return;
        }
        boolean existe = categorias.buscarPorId(categoriaId).filter(c -> !c.eliminado()).isPresent();
        if (!existe) {
            throw new IllegalArgumentException("La categoría no existe o fue eliminada");
        }
    }
}
