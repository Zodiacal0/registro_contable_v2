package com.registrogastos.ui;

import com.registrogastos.dominio.Abono;
import com.registrogastos.dominio.Deuda;
import com.registrogastos.servicio.EstadoDeuda;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Deudas en dos pestañas: "Debo" (sus abonos son gastos) y "Me deben" (lo que me pagan no es
 * gasto). En el detalle: saldo pendiente, progreso, abonar y el historial de abonos.
 */
final class VistaDeudas extends VistaListaDetalle {

    private enum Modo { VER, CREAR, EDITAR }

    private final ToggleGroup pestanas = new ToggleGroup();
    private final ToggleButton debo = pestana("Debo");
    private final ToggleButton meDeben = pestana("Me deben");
    private String elegidaId;
    private Modo modo = Modo.VER;

    VistaDeudas(ModeloUi modelo) {
        super(modelo, "Deudas");
        debo.setSelected(true);
        // Siempre hay una pestaña elegida; al cambiar de pestaña se vuelve a "ver"
        pestanas.selectedToggleProperty().addListener((obs, anterior, nueva) -> {
            if (nueva == null) {
                anterior.setSelected(true);
                return;
            }
            elegidaId = null;
            cambiarModo(Modo.VER);
        });
        var segmentado = new HBox(debo, meDeben);
        segmentado.getStyleClass().add("segmentado");
        ponerAccion(segmentado, Nodos.boton("+  Nueva deuda", () -> cambiarModo(Modo.CREAR), "boton-pildora"));
        pintar();
    }

    private ToggleButton pestana(String texto) {
        var boton = new ToggleButton(texto);
        boton.getStyleClass().add("segmento");
        boton.setToggleGroup(pestanas);
        boton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(boton, Priority.ALWAYS);
        return boton;
    }

    private Deuda.Tipo tipoActual() {
        return meDeben.isSelected() ? Deuda.Tipo.ME_DEBEN : Deuda.Tipo.DEBO;
    }

    private void cambiarModo(Modo nuevo) {
        modo = nuevo;
        pintar();
    }

    @Override
    protected void pintar() {
        Deuda.Tipo tipo = tipoActual();
        List<EstadoDeuda> deudas = modelo.deudas().stream().filter(e -> e.deuda().tipo() == tipo).toList();
        BigDecimal pendiente = deudas.stream().map(EstadoDeuda::saldo).reduce(BigDecimal.ZERO, BigDecimal::add);
        subtitulo.setText(deudas.isEmpty() ? (tipo == Deuda.Tipo.DEBO ? "Nada pendiente por pagar" : "Nadie te debe")
                : (tipo == Deuda.Tipo.DEBO ? "Debes " : "Te deben ") + Formato.monto(pendiente));

        Optional<EstadoDeuda> elegida = deudas.stream()
                .filter(e -> e.deuda().id().equals(elegidaId))
                .findFirst()
                .or(() -> deudas.stream().findFirst());

        lista.getChildren().clear();
        for (EstadoDeuda e : deudas) {
            boolean esta = modo != Modo.CREAR && elegida.filter(e::equals).isPresent();
            lista.getChildren().add(itemLista(item(e), esta, () -> {
                elegidaId = e.deuda().id();
                cambiarModo(Modo.VER);
            }));
        }

        if (modo == Modo.CREAR || (deudas.isEmpty() && modo != Modo.EDITAR)) {
            detalle.getChildren().setAll(formulario(null));
        } else if (modo == Modo.EDITAR && elegida.isPresent()) {
            detalle.getChildren().setAll(formulario(elegida.get()));
        } else {
            detalle.getChildren().setAll(elegida.map(this::verDeuda).orElseGet(List::of));
        }
    }

    private Node item(EstadoDeuda e) {
        String estado = e.saldada() ? "Saldada" : "Pendiente " + Formato.monto(e.saldo());
        var titulo = new HBox(10, Nodos.etiqueta(e.deuda().nombre(), "leyenda-nombre"), Nodos.espaciador(),
                Nodos.etiqueta(Formato.monto(e.deuda().monto()), "leyenda-monto"));
        titulo.setAlignment(Pos.CENTER_LEFT);
        var caja = new VBox(8, titulo, barraProgreso(e.proporcionPagada(), "-c-4", false),
                Nodos.etiqueta(estado, "leyenda-detalle"));
        if (e.saldada()) {
            caja.getStyleClass().add("lista-item-completo");
        }
        return caja;
    }

    // ---- Ver ----

    private List<Node> verDeuda(EstadoDeuda e) {
        Deuda deuda = e.deuda();
        String limite = deuda.fechaLimite() == null ? "sin fecha límite"
                : "límite " + Formato.dia(deuda.fechaLimite(), modelo.hoy()).toLowerCase();
        var acciones = new HBox(4,
                Nodos.boton("Editar", () -> cambiarModo(Modo.EDITAR), "boton-texto"),
                botonConfirmar("Eliminar", "Confirmar: eliminar deuda", () -> {
                    modelo.eliminarDeuda(deuda.id());
                    elegidaId = null;
                }));
        acciones.setTranslateX(-8);
        var encabezado = new VBox(4, Nodos.etiqueta(deuda.nombre(), "titulo"),
                Nodos.etiqueta("Total " + Formato.monto(deuda.monto()) + "  ·  desde "
                        + Formato.fechaCorta(deuda.fecha(), modelo.hoy()).toLowerCase() + "  ·  " + limite, "subtitulo"),
                acciones);
        encabezado.getStyleClass().add("encabezado");

        var cifra = new HBox(10, Nodos.etiqueta(Formato.monto(e.saldo()), "cifra-grande"),
                Nodos.etiqueta(e.saldada() ? "saldada" : "pendiente", "subtitulo"));
        cifra.setAlignment(Pos.BASELINE_LEFT);
        var datos = new HBox(8, dato("Pagado " + Formato.monto(e.pagado()) + "  ·  "
                + Formato.porcentaje(Math.min(1, e.proporcionPagada()))));
        var progreso = new VBox(14, cifra, barraProgreso(e.proporcionPagada(), "-c-5", true), datos);

        var secciones = new ArrayList<Node>(List.of(encabezado, progreso));
        if (!e.saldada()) {
            secciones.add(abonar(deuda));
        }
        secciones.add(historial(deuda));
        return secciones;
    }

    private Node abonar(Deuda deuda) {
        boolean debo = deuda.tipo() == Deuda.Tipo.DEBO;
        TextField monto = Campos.monto(null);
        DatePicker fecha = Campos.fecha(modelo.hoy());
        var error = etiquetaError();
        Runnable abonar = () -> ejecutar(error, () ->
                modelo.abonarDeuda(deuda.id(), Formato.leerMonto(monto.getText()), fecha.getValue(), null, null));
        monto.setOnAction(e -> abonar.run());

        var fila = new HBox(10, Campos.etiquetado("Monto", monto), Campos.etiquetado("Fecha", fecha));
        HBox.setHgrow(fila.getChildren().getFirst(), Priority.ALWAYS);
        var boton = Nodos.boton(debo ? "Abonar" : "Registrar pago recibido", abonar, "boton-pildora");
        return new VBox(14,
                encabezadoSeccion(debo ? "Abonar" : "Me pagaron",
                        debo ? "El abono se registra como gasto" : "No cuenta como gasto"),
                fila, error, boton);
    }

    private Node historial(Deuda deuda) {
        List<Abono> abonos = modelo.abonosDe(deuda.id());
        if (abonos.isEmpty()) {
            return new VBox(14, encabezadoSeccion("Historial", ""), vacio("Todavía no hay abonos."));
        }
        var filas = new ArrayList<Node>();
        for (Abono a : abonos) {
            filas.add(filaHistorial(Formato.dia(a.fecha(), modelo.hoy()), Formato.monto(a.monto()),
                    () -> modelo.eliminarAbonoDeuda(deuda.id(), a.id())));
        }
        return new VBox(14, encabezadoSeccion("Historial", abonos.size() + (abonos.size() == 1 ? " abono" : " abonos")),
                tabla(filas.toArray(Node[]::new)));
    }

    // ---- Crear / editar ----

    /** @param estado null = crear una nueva (del tipo de la pestaña actual) */
    private List<Node> formulario(EstadoDeuda estado) {
        boolean nueva = estado == null;
        Deuda deuda = nueva ? null : estado.deuda();
        Deuda.Tipo tipo = nueva ? tipoActual() : deuda.tipo();

        TextField nombre = Campos.texto(nueva ? "" : deuda.nombre(),
                tipo == Deuda.Tipo.DEBO ? "Ej. Tarjeta de crédito" : "Ej. Préstamo a Carlos");
        TextField monto = Campos.monto(nueva ? null : deuda.monto());
        DatePicker inicio = Campos.fecha(nueva ? modelo.hoy() : deuda.fecha());
        inicio.setDisable(!nueva); // la fecha de inicio no se edita: ordena el historial
        DatePicker limite = Campos.fecha(nueva || deuda.fechaLimite() == null
                ? modelo.hoy().plusMonths(6) : deuda.fechaLimite());
        var conLimite = Campos.interruptor("Con fecha límite");
        conLimite.setSelected(!nueva && deuda.fechaLimite() != null);
        limite.visibleProperty().bind(conLimite.selectedProperty());
        limite.managedProperty().bind(limite.visibleProperty());
        var filaLimite = new HBox(10, Nodos.etiqueta("Con fecha límite", "interruptor-texto"), Nodos.espaciador(), conLimite);
        filaLimite.setAlignment(Pos.CENTER_LEFT);

        var error = etiquetaError();
        Runnable guardar = () -> ejecutar(error, () -> {
            LocalDate fechaLimite = conLimite.isSelected() ? limite.getValue() : null;
            BigDecimal valor = Formato.leerMonto(monto.getText());
            if (nueva) {
                elegidaId = modelo.crearDeuda(nombre.getText(), tipo, valor, inicio.getValue(), fechaLimite).id();
            } else {
                modelo.editarDeuda(deuda.id(), nombre.getText(), valor, fechaLimite);
            }
            cambiarModo(Modo.VER);
        });

        var botones = new HBox(4, Nodos.boton(nueva ? "Crear deuda" : "Guardar cambios", guardar, "boton-pildora"));
        if (modelo.deudas().stream().anyMatch(e -> e.deuda().tipo() == tipo)) {
            botones.getChildren().add(Nodos.boton("Cancelar", () -> cambiarModo(Modo.VER), "boton-texto"));
        }
        botones.setAlignment(Pos.CENTER_LEFT);

        String titulo = nueva ? (tipo == Deuda.Tipo.DEBO ? "Nueva deuda" : "Alguien te debe") : "Editar deuda";
        String sub = nueva ? (tipo == Deuda.Tipo.DEBO
                ? "Tarjeta, préstamo o algo que le debes a alguien. Cada abono se registrará como gasto."
                : "Dinero que prestaste. Lo que te paguen bajará el saldo, sin contar como gasto.")
                : deuda.nombre();
        var subtitulo = Nodos.etiqueta(sub, "subtitulo");
        subtitulo.setWrapText(true);
        var encabezado = new VBox(4, Nodos.etiqueta(titulo, "titulo"), subtitulo);
        encabezado.getStyleClass().add("encabezado");

        var fechas = new HBox(10, Campos.etiquetado("Desde", inicio));
        HBox.setHgrow(fechas.getChildren().getFirst(), Priority.ALWAYS);
        var campos = new VBox(16, Campos.etiquetado(tipo == Deuda.Tipo.DEBO ? "A quién o qué" : "Quién te debe", nombre),
                Campos.etiquetado("Monto total", monto), fechas, new VBox(12, filaLimite, limite), error, botones);
        campos.setMaxWidth(520);
        return List.of(encabezado, campos);
    }
}
