package com.registrogastos.consola;

import com.registrogastos.dominio.Categoria;
import com.registrogastos.dominio.Gasto;
import com.registrogastos.servicio.GastoServicio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Interfaz TEMPORAL por consola para probar el núcleo. Se reemplaza por JavaFX
 * cuando esté la dirección gráfica; el servicio no cambia.
 * Usa java.lang.IO (Java 25): println/readln sin System.out ni Scanner.
 */
public final class MenuConsola {

    private final GastoServicio servicio;

    public MenuConsola(GastoServicio servicio) {
        this.servicio = servicio;
    }

    public void iniciar() {
        while (true) {
            IO.println("""

                    1) Registrar gasto   2) Gastos del mes   3) Nueva categoría
                    4) Eliminar gasto    0) Salir""");
            String opcion = IO.readln("> ");
            if (opcion == null) {
                return; // fin de la entrada (Ctrl+Z / stdin cerrado)
            }
            try {
                switch (opcion.strip()) {
                    case "1" -> registrar();
                    case "2" -> listarMes();
                    case "3" -> IO.println("Creada: " + servicio.crearCategoria(IO.readln("Nombre: ")).nombre());
                    case "4" -> eliminar();
                    case "0" -> { return; }
                    default -> IO.println("Opción no válida");
                }
            } catch (IllegalArgumentException | DateTimeParseException
                     | NoSuchElementException | IndexOutOfBoundsException e) {
                IO.println("Error: " + e.getMessage());
            }
        }
    }

    private void registrar() {
        String descripcion = IO.readln("Descripción: ");
        var monto = new BigDecimal(IO.readln("Monto (ej. 12.50): ").strip());
        String fechaTexto = IO.readln("Fecha AAAA-MM-DD (vacío = hoy): ").strip();
        LocalDate fecha = fechaTexto.isEmpty() ? LocalDate.now() : LocalDate.parse(fechaTexto);
        Gasto gasto = servicio.registrar(descripcion, monto, fecha, elegirCategoria());
        IO.println("Registrado: " + gasto.descripcion() + " " + gasto.monto());
    }

    private String elegirCategoria() {
        List<Categoria> categorias = servicio.categorias();
        if (categorias.isEmpty()) {
            return null;
        }
        for (int i = 0; i < categorias.size(); i++) {
            IO.println("  " + (i + 1) + ") " + categorias.get(i).nombre());
        }
        String eleccion = IO.readln("Categoría (vacío = ninguna): ").strip();
        return eleccion.isEmpty() ? null : categorias.get(Integer.parseInt(eleccion) - 1).id();
    }

    private void listarMes() {
        var mes = servicio.mesActual();
        List<Gasto> gastos = servicio.delMes(mes);
        Map<String, String> nombres = servicio.categorias().stream()
                .collect(Collectors.toMap(Categoria::id, Categoria::nombre));
        IO.println("Gastos de " + mes + ":");
        for (int i = 0; i < gastos.size(); i++) {
            Gasto g = gastos.get(i);
            IO.println("%3d) %s  %-25s %10s  %s".formatted(i + 1, g.fecha(), g.descripcion(), g.monto(),
                    g.categoriaId() == null ? "" : nombres.getOrDefault(g.categoriaId(), "?")));
        }
        IO.println("Total: " + servicio.total(gastos));
    }

    private void eliminar() {
        List<Gasto> gastos = servicio.delMes(servicio.mesActual());
        listarMes();
        if (gastos.isEmpty()) {
            return;
        }
        int numero = Integer.parseInt(IO.readln("Número a eliminar: ").strip());
        servicio.eliminar(gastos.get(numero - 1).id());
        IO.println("Eliminado");
    }
}
