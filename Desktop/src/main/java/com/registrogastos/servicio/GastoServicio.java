package com.registrogastos.servicio;

import com.registrogastos.dominio.Categoria;
import com.registrogastos.dominio.Gasto;
import com.registrogastos.persistencia.CategoriaRepositorio;
import com.registrogastos.persistencia.GastoRepositorio;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Casos de uso de la app. La UI (consola hoy, JavaFX mañana) solo habla con esta clase.
 * El reloj se inyecta para que las pruebas controlen "ahora".
 */
public final class GastoServicio {

    private final GastoRepositorio gastos;
    private final CategoriaRepositorio categorias;
    private final Clock reloj;

    public GastoServicio(GastoRepositorio gastos, CategoriaRepositorio categorias, Clock reloj) {
        this.gastos = gastos;
        this.categorias = categorias;
        this.reloj = reloj;
    }

    public Gasto registrar(String descripcion, BigDecimal monto, LocalDate fecha, String categoriaId) {
        validarCategoria(categoriaId);
        var gasto = Gasto.nuevo(descripcion, monto, fecha, categoriaId, reloj.instant());
        gastos.guardar(gasto);
        return gasto;
    }

    public Gasto editar(String id, String descripcion, BigDecimal monto, LocalDate fecha, String categoriaId) {
        validarCategoria(categoriaId);
        var editado = buscarActivo(id).editar(descripcion, monto, fecha, categoriaId, reloj.instant());
        gastos.guardar(editado);
        return editado;
    }

    public void eliminar(String id) {
        gastos.guardar(buscarActivo(id).eliminar(reloj.instant()));
    }

    public List<Gasto> delMes(YearMonth mes) {
        return gastos.listarEntre(mes.atDay(1), mes.atEndOfMonth());
    }

    public BigDecimal total(List<Gasto> lista) {
        return lista.stream().map(Gasto::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public YearMonth mesActual() {
        return YearMonth.now(reloj);
    }

    public Categoria crearCategoria(String nombre) {
        var categoria = Categoria.nueva(nombre, reloj.instant());
        categorias.guardar(categoria);
        return categoria;
    }

    public List<Categoria> categorias() {
        return categorias.listarActivas();
    }

    private Gasto buscarActivo(String id) {
        return gastos.buscarPorId(id)
                .filter(g -> !g.eliminado())
                .orElseThrow(() -> new NoSuchElementException("No existe el gasto " + id));
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
