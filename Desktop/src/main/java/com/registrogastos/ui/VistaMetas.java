package com.registrogastos.ui;

import com.registrogastos.dominio.Abono;
import com.registrogastos.dominio.Meta;
import com.registrogastos.servicio.EstadoMeta;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Metas de ahorro: lista con el progreso de cada una y, en el detalle, cuánto llevas, cuánto
 * falta, cuánto aportar al mes para llegar a tiempo, aportar o retirar, y el historial.
 */
final class VistaMetas extends VistaListaDetalle {

    private enum Modo { VER, CREAR, EDITAR }

    private String elegidaId;
    private Modo modo = Modo.VER;

    VistaMetas(ModeloUi modelo) {
        super(modelo, "Metas");
        ponerAccion(Nodos.boton("+  Nueva meta", () -> cambiarModo(Modo.CREAR), "boton-pildora"));
        pintar();
    }

    private void cambiarModo(Modo nuevo) {
        modo = nuevo;
        pintar();
    }

    @Override
    protected void pintar() {
        List<EstadoMeta> metas = modelo.metas();
        BigDecimal ahorrado = metas.stream().map(EstadoMeta::ahorrado).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal objetivo = metas.stream().map(e -> e.meta().objetivo()).reduce(BigDecimal.ZERO, BigDecimal::add);
        subtitulo.setText(metas.isEmpty() ? "Ahorra con un objetivo"
                : "Ahorrado " + Formato.monto(ahorrado) + " de " + Formato.monto(objetivo));

        Optional<EstadoMeta> elegida = metas.stream()
                .filter(e -> e.meta().id().equals(elegidaId))
                .findFirst()
                .or(() -> metas.stream().findFirst());

        lista.getChildren().clear();
        for (EstadoMeta e : metas) {
            boolean esta = modo != Modo.CREAR && elegida.filter(e::equals).isPresent();
            lista.getChildren().add(itemLista(item(e), esta, () -> {
                elegidaId = e.meta().id();
                cambiarModo(Modo.VER);
            }));
        }

        if (modo == Modo.CREAR || (metas.isEmpty() && modo != Modo.EDITAR)) {
            detalle.getChildren().setAll(formulario(null));
        } else if (modo == Modo.EDITAR && elegida.isPresent()) {
            detalle.getChildren().setAll(formulario(elegida.get().meta()));
        } else {
            detalle.getChildren().setAll(elegida.map(this::verMeta).orElseGet(List::of));
        }
    }

    private Node item(EstadoMeta e) {
        String estado = e.completada() ? "Completada" : "Faltan " + Formato.monto(e.restante());
        var titulo = new HBox(10, muestra(e.meta().color()), Nodos.etiqueta(e.meta().nombre(), "leyenda-nombre"),
                Nodos.espaciador(), Nodos.etiqueta(Formato.porcentaje(Math.min(1, e.proporcion())), "leyenda-monto"));
        titulo.setAlignment(Pos.CENTER_LEFT);
        var caja = new VBox(8, titulo, barraProgreso(e.proporcion(), e.meta().color(), false),
                Nodos.etiqueta(estado, "leyenda-detalle"));
        if (e.completada()) {
            caja.getStyleClass().add("lista-item-completo");
        }
        return caja;
    }

    // ---- Ver ----

    private List<Node> verMeta(EstadoMeta e) {
        Meta meta = e.meta();
        String para = meta.fechaObjetivo() == null ? "sin fecha límite"
                : "para el " + Formato.dia(meta.fechaObjetivo(), modelo.hoy()).toLowerCase();
        var acciones = new HBox(4,
                Nodos.boton("Editar", () -> cambiarModo(Modo.EDITAR), "boton-texto"),
                botonConfirmar("Eliminar", "Confirmar: eliminar meta", () -> {
                    modelo.eliminarMeta(meta.id());
                    elegidaId = null;
                }));
        acciones.setTranslateX(-8);
        var encabezado = new VBox(4, Nodos.etiqueta(meta.nombre(), "titulo"),
                Nodos.etiqueta("Objetivo " + Formato.monto(meta.objetivo()) + "  ·  " + para, "subtitulo"), acciones);
        encabezado.getStyleClass().add("encabezado");

        // Progreso: lo ahorrado en grande, la barra en el color de la meta y los datos clave
        var cifra = new HBox(10, Nodos.etiqueta(Formato.monto(e.ahorrado()), "cifra-grande"),
                Nodos.etiqueta("de " + Formato.monto(meta.objetivo()), "subtitulo"));
        cifra.setAlignment(Pos.BASELINE_LEFT);
        var datos = new HBox(8);
        if (e.completada()) {
            datos.getChildren().add(dato("Completada"));
        } else {
            datos.getChildren().add(dato("Faltan " + Formato.monto(e.restante())));
            e.aporteMensualSugerido().ifPresent(m ->
                    datos.getChildren().add(dato("≈ " + Formato.monto(m) + " al mes para llegar a tiempo")));
        }
        var progreso = new VBox(14, cifra, barraProgreso(e.proporcion(), meta.color(), true), datos);

        return List.of(encabezado, progreso, aportar(meta), historial(meta));
    }

    private Node aportar(Meta meta) {
        TextField monto = Campos.monto(null);
        DatePicker fecha = Campos.fecha(modelo.hoy());
        var error = etiquetaError();

        Runnable aportar = () -> ejecutar(error, () -> modelo.aportar(meta.id(), Formato.leerMonto(monto.getText()), fecha.getValue()));
        Runnable retirar = () -> ejecutar(error, () -> modelo.retirar(meta.id(), Formato.leerMonto(monto.getText()), fecha.getValue()));
        monto.setOnAction(e -> aportar.run());

        var fila = new HBox(10, Campos.etiquetado("Monto", monto), Campos.etiquetado("Fecha", fecha));
        HBox.setHgrow(fila.getChildren().getFirst(), Priority.ALWAYS);
        var botones = new HBox(4, Nodos.boton("Aportar", aportar, "boton-pildora"), Nodos.boton("Retirar", retirar, "boton-texto"));
        botones.setAlignment(Pos.CENTER_LEFT);
        return new VBox(14, encabezadoSeccion("Aportar", "Los aportes no cuentan como gasto"), fila, error, botones);
    }

    private Node historial(Meta meta) {
        List<Abono> aportes = modelo.aportesDe(meta.id());
        if (aportes.isEmpty()) {
            return new VBox(14, encabezadoSeccion("Historial", ""), vacio("Todavía no hay aportes."));
        }
        var filas = new ArrayList<Node>();
        for (Abono a : aportes) {
            String signo = a.monto().signum() > 0 ? "+" : "−";
            filas.add(filaHistorial(Formato.dia(a.fecha(), modelo.hoy()), signo + Formato.monto(a.monto().abs()),
                    () -> modelo.eliminarAporte(a.id())));
        }
        return new VBox(14, encabezadoSeccion("Historial", aportes.size() + (aportes.size() == 1 ? " movimiento" : " movimientos")),
                tabla(filas.toArray(Node[]::new)));
    }

    // ---- Crear / editar ----

    /** @param meta null = crear una nueva */
    private List<Node> formulario(Meta meta) {
        boolean nueva = meta == null;
        TextField nombre = Campos.texto(nueva ? "" : meta.nombre(), "Ej. Fondo de emergencia");
        TextField objetivo = Campos.monto(nueva ? null : meta.objetivo());
        DatePicker fecha = Campos.fecha(nueva || meta.fechaObjetivo() == null
                ? modelo.hoy().plusMonths(6) : meta.fechaObjetivo());
        var conFecha = Campos.interruptor("Con fecha objetivo");
        conFecha.setSelected(!nueva && meta.fechaObjetivo() != null);
        Movimiento.desplegarCon(fecha, conFecha.selectedProperty());
        var filaFecha = new HBox(10, Nodos.etiqueta("Con fecha objetivo", "interruptor-texto"), Nodos.espaciador(), conFecha);
        filaFecha.setAlignment(Pos.CENTER_LEFT);

        var error = etiquetaError();
        Runnable guardar = () -> ejecutar(error, () -> {
            LocalDate limite = conFecha.isSelected() ? fecha.getValue() : null;
            BigDecimal monto = Formato.leerMonto(objetivo.getText());
            if (nueva) {
                elegidaId = modelo.crearMeta(nombre.getText(), monto, limite).id();
            } else {
                modelo.editarMeta(meta.id(), nombre.getText(), monto, limite);
            }
            cambiarModo(Modo.VER);
        });

        var botones = new HBox(4, Nodos.boton(nueva ? "Crear meta" : "Guardar cambios", guardar, "boton-pildora"));
        if (!modelo.metas().isEmpty()) {
            botones.getChildren().add(Nodos.boton("Cancelar", () -> cambiarModo(Modo.VER), "boton-texto"));
        }
        botones.setAlignment(Pos.CENTER_LEFT);

        var encabezado = new VBox(4, Nodos.etiqueta(nueva ? "Nueva meta" : "Editar meta", "titulo"),
                Nodos.etiqueta(nueva ? "Un objetivo de ahorro, con o sin fecha." : meta.nombre(), "subtitulo"));
        encabezado.getStyleClass().add("encabezado");
        var campos = new VBox(16, Campos.etiquetado("Nombre", nombre), Campos.etiquetado("Objetivo", objetivo),
                new VBox(12, filaFecha, fecha), error, botones);
        campos.setMaxWidth(520);
        return List.of(encabezado, campos);
    }
}
