package com.registrogastos.ui;

import com.registrogastos.dominio.Categoria;
import com.registrogastos.dominio.Deuda;
import com.registrogastos.dominio.Gasto;
import com.registrogastos.servicio.EstadoDeuda;
import com.registrogastos.servicio.EstadoMeta;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Panel fijo a la derecha para registrar gastos, siempre visible (como el panel de
 * órdenes de Binance). Si el modelo tiene un gasto seleccionado, el mismo panel pasa a
 * modo edición; así no hace falta un modal.
 */
final class PanelGasto extends VBox {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ModeloUi modelo;
    private final Label titulo = Nodos.etiqueta("", "panel-titulo");
    private final Button salirEdicion = Iconos.CERRAR.boton("Salir de la edición", this::salirEdicion);
    private final TextField monto = new TextField();
    private final TextField descripcion = new TextField();
    private final DatePicker fecha = new DatePicker();
    private final FlowPane chips = new FlowPane(8, 8);
    private final ToggleGroup categorias = new ToggleGroup();
    private final Label error = Nodos.etiqueta("", "error");
    private final Label aviso = Nodos.etiqueta("", "aviso");
    private final PauseTransition apagarAviso = new PauseTransition(Duration.seconds(4));
    private final Label errorMonto = Nodos.etiqueta("", "error");
    private final VBox cajaMonto = new VBox(4);
    private final Button guardar = new Button();
    private final Button eliminar = new Button("Eliminar gasto");
    private final Label hoyDetalle = Nodos.etiqueta("", "hoy-detalle");
    private final Label hoyTotal = Nodos.etiqueta("", "hoy-total");
    private final VBox contenido;

    // "Se repite": convierte el gasto nuevo en una recurrencia
    private final ToggleButton repetir = new ToggleButton();
    private final SelectorFrecuencia frecuencia = new SelectorFrecuencia();
    private final Label etiquetaFecha = Nodos.etiqueta("Fecha", "campo-etiqueta");
    private final VBox cajaRepetir;

    // "Va a una deuda o meta": el monto abona a una deuda que debo o se aparta para una meta
    private final ToggleButton destinar = new ToggleButton();
    private final FlowPane destinos = new FlowPane(8, 8);
    private final ToggleGroup grupoDestinos = new ToggleGroup();
    private final Label ayudaDestino = Nodos.etiqueta("", "destino-ayuda");
    private final VBox cajaDestino;

    /** @param alOcultar qué hacer con el botón "»" (la ventana decide cómo se oculta el panel) */
    PanelGasto(ModeloUi modelo, Runnable alOcultar) {
        this.modelo = modelo;
        getStyleClass().add("panel");
        setMinWidth(340);
        setPrefWidth(360);
        setMaxWidth(360);

        configurarCampos();
        salirEdicion.getStyleClass().add("boton-icono-chico");
        var ocultar = Iconos.OCULTAR_PANEL.boton("Ocultar panel", alOcultar);
        ocultar.getStyleClass().add("boton-icono-chico");
        var encabezado = new HBox(6, titulo, Nodos.espaciador(), salirEdicion, ocultar);
        encabezado.setAlignment(Pos.CENTER_LEFT);

        cajaMonto.getChildren().addAll(Nodos.etiqueta("Monto", "campo-etiqueta"), monto);
        cajaMonto.getStyleClass().add("caja-monto");
        // Todo el recuadro se comporta como el campo: un clic en cualquier parte pone el cursor
        cajaMonto.setOnMouseClicked(e -> monto.requestFocus());
        errorMonto.managedProperty().bind(errorMonto.textProperty().isNotEmpty());
        // Al corregir el monto, el aviso se va
        monto.textProperty().addListener((obs, antes, ahora) -> {
            errorMonto.setText("");
            cajaMonto.getStyleClass().remove("caja-monto-error");
        });

        guardar.getStyleClass().addAll("boton", "boton-primario", "boton-ancho");
        guardar.setMaxWidth(Double.MAX_VALUE);
        guardar.setOnAction(e -> guardar());
        eliminar.getStyleClass().addAll("boton", "boton-peligro", "boton-ancho");
        eliminar.setMaxWidth(Double.MAX_VALUE);
        eliminar.setOnAction(e -> eliminar());
        error.managedProperty().bind(error.textProperty().isNotEmpty());
        aviso.managedProperty().bind(aviso.textProperty().isNotEmpty());
        aviso.setWrapText(true);
        aviso.setMinHeight(Region.USE_PREF_SIZE); // sin esto el VBox lo comprime a una línea con "…"
        apagarAviso.setOnFinished(e -> aviso.setText(""));

        cajaRepetir = construirRepetir();
        cajaDestino = construirDestino();

        var hoy = new HBox(new VBox(2, Nodos.etiqueta("Hoy", "hoy-titulo"), hoyDetalle), Nodos.espaciador(), hoyTotal);
        hoy.setAlignment(Pos.CENTER_LEFT);
        hoy.getStyleClass().add("hoy");

        var relleno = new Region();
        VBox.setVgrow(relleno, Priority.ALWAYS);
        contenido = new VBox(20,
                encabezado,
                new VBox(8, cajaMonto, errorMonto),
                campo("Descripción", descripcion),
                new VBox(8, etiquetaFecha, fecha),
                campo("Categoría", chips),
                cajaRepetir,
                cajaDestino,
                error,
                aviso,
                relleno,
                hoy,
                new VBox(10, guardar, eliminar),
                Nodos.etiqueta("Enter guarda  ·  Esc limpia  ·  Ctrl+N vuelve aquí", "panel-atajos"));
        // Si la ventana es baja, el panel se desplaza en vez de cortar lo de abajo;
        // fitToHeight estira el contenido cuando sí cabe, así el botón queda al fondo
        var desplazable = new ScrollPane(contenido);
        desplazable.setFitToWidth(true);
        desplazable.setFitToHeight(true);
        desplazable.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        desplazable.getStyleClass().add("vista");
        DesplazamientoSuave.aplicar(desplazable);
        VBox.setVgrow(desplazable, Priority.ALWAYS);
        getChildren().add(desplazable);
        // Al abrir los destinos, el panel baja hasta el fondo (destinos y botón) por si quedaron
        // fuera de la vista. runLater: espera a que el layout cuente ya los chips recién mostrados
        destinar.selectedProperty().addListener((obs, antes, activo) -> {
            if (activo) {
                Platform.runLater(() -> {
                    double desde = desplazable.getVvalue();
                    Movimiento.progreso(Duration.millis(320), f -> desplazable.setVvalue(desde + (1 - desde) * f));
                });
            }
        });

        // Esc: sale de la edición o limpia el formulario
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                salirEdicion();
                e.consume();
            }
        });
        // Siempre debe quedar una categoría elegida: un clic sobre la activa no la apaga.
        // Si el anterior ya no está en el grupo (se repintaron los chips), no se toca.
        categorias.selectedToggleProperty().addListener((obs, anterior, nuevo) -> {
            if (nuevo == null && categorias.getToggles().contains(anterior)) {
                anterior.setSelected(true);
            }
        });
        modelo.seleccionadoProperty().addListener((obs, anterior, gasto) -> cargar(gasto));
        modelo.resumenProperty().addListener((obs, anterior, resumen) -> {
            pintarChips(categoriaElegidaId());
            pintarDestinos();
            pintarHoy();
        });
        cargar(null);
        pintarHoy();
    }

    /**
     * Interruptor "Se repite" y, al activarlo, la frecuencia. La fecha pasa a llamarse
     * "Primer cobro" y el botón "Crear recurrente".
     */
    private VBox construirRepetir() {
        frecuencia.visibleProperty().bind(repetir.selectedProperty());
        frecuencia.managedProperty().bind(frecuencia.visibleProperty());
        etiquetaFecha.textProperty().bind(Bindings.when(repetir.selectedProperty())
                .then("Primer cobro").otherwise("Fecha"));
        repetir.selectedProperty().addListener((obs, antes, activo) -> {
            if (activo) {
                destinar.setSelected(false); // un gasto recurrente no abona ni aporta
            }
            actualizarGuardar();
        });

        repetir.getStyleClass().add("repetir");
        var caja = new VBox(12, filaInterruptor(repetir, Iconos.REPETIR, "Se repite"), frecuencia);
        caja.getStyleClass().add("caja-interruptor");
        return caja;
    }

    /**
     * Interruptor "Va a una deuda o meta" y, al activarlo, los destinos posibles: deudas que
     * debo con saldo y metas sin completar. Si es una meta, el dinero se aparta (no es gasto),
     * así que descripción y categoría no aplican y se desactivan.
     */
    private VBox construirDestino() {
        var opciones = new VBox(10, destinos, ayudaDestino);
        opciones.visibleProperty().bind(destinar.selectedProperty());
        opciones.managedProperty().bind(opciones.visibleProperty());
        ayudaDestino.setWrapText(true);
        ayudaDestino.setMinHeight(Region.USE_PREF_SIZE);

        destinar.selectedProperty().addListener((obs, antes, activo) -> {
            if (activo) {
                repetir.setSelected(false);
            }
            alCambiarDestino();
        });
        // Igual que con las categorías: siempre queda un destino elegido
        grupoDestinos.selectedToggleProperty().addListener((obs, anterior, nuevo) -> {
            if (nuevo == null && grupoDestinos.getToggles().contains(anterior)) {
                anterior.setSelected(true);
            }
            alCambiarDestino();
        });

        destinar.getStyleClass().add("destinar");
        var caja = new VBox(12, filaInterruptor(destinar, Iconos.DEUDA, "Va a una deuda o meta"), opciones);
        caja.getStyleClass().add("caja-interruptor");
        return caja;
    }

    /** Ícono, texto y un interruptor tipo iOS; toda la fila lo activa, no solo la perilla. */
    private static HBox filaInterruptor(ToggleButton interruptor, Iconos icono, String texto) {
        var perilla = new Region();
        perilla.getStyleClass().add("interruptor-perilla");
        interruptor.setGraphic(perilla);
        interruptor.getStyleClass().add("interruptor");
        interruptor.setAccessibleText(texto);

        var fila = new HBox(10, icono.crear("icono-chico"), Nodos.etiqueta(texto, "interruptor-texto"),
                Nodos.espaciador(), interruptor);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.setOnMouseClicked(e -> {
            if (e.getTarget() != interruptor) {
                interruptor.fire();
            }
        });
        return fila;
    }

    /** Repinta los destinos conservando el elegido; sin destinos posibles, la caja no se muestra. */
    private void pintarDestinos() {
        var anterior = grupoDestinos.getSelectedToggle();
        String elegido = idDestino(anterior == null ? null : anterior.getUserData());
        grupoDestinos.getToggles().clear();
        destinos.getChildren().clear();
        modelo.deudas().stream()
                .filter(d -> d.deuda().tipo() == Deuda.Tipo.DEBO && !d.saldada())
                .forEach(d -> destinos.getChildren().add(chipDestino(d.deuda().nombre(), Iconos.DEUDA, d)));
        modelo.metas().stream()
                .filter(m -> !m.completada())
                .forEach(m -> destinos.getChildren().add(chipDestino(m.meta().nombre(), Iconos.META, m)));

        boolean hay = !grupoDestinos.getToggles().isEmpty();
        if (hay) {
            grupoDestinos.getToggles().stream()
                    .filter(t -> idDestino(t.getUserData()).equals(elegido))
                    .findFirst()
                    .orElse(grupoDestinos.getToggles().getFirst())
                    .setSelected(true);
        } else {
            destinar.setSelected(false);
        }
        boolean visible = hay && modelo.seleccionadoProperty().get() == null;
        cajaDestino.setVisible(visible);
        cajaDestino.setManaged(visible);
        alCambiarDestino();
    }

    private ToggleButton chipDestino(String texto, Iconos icono, Object estado) {
        var chip = new ToggleButton(texto, icono.crear("icono-chico"));
        chip.getStyleClass().add("chip");
        chip.setUserData(estado);
        chip.setToggleGroup(grupoDestinos);
        return chip;
    }

    private static String idDestino(Object destino) {
        return switch (destino) {
            case EstadoDeuda d -> d.deuda().id();
            case EstadoMeta m -> m.meta().id();
            case null, default -> "";
        };
    }

    /** El destino elegido, o null si el interruptor está apagado. */
    private Object destino() {
        var elegido = grupoDestinos.getSelectedToggle();
        return destinar.isSelected() && elegido != null ? elegido.getUserData() : null;
    }

    /** Ajusta ayuda, campos y botón al destino: lo que cuenta como gasto y lo que no. */
    private void alCambiarDestino() {
        Object destino = destino();
        boolean aMeta = destino instanceof EstadoMeta;
        descripcion.setDisable(aMeta);
        chips.setDisable(aMeta);
        ayudaDestino.setText(switch (destino) {
            case EstadoDeuda d -> "Saldo pendiente " + Formato.monto(d.saldo()) + ". El abono cuenta como gasto.";
            case EstadoMeta m -> "Faltan " + Formato.monto(m.restante()) + ". Se aparta para la meta: no cuenta como gasto.";
            case null, default -> "";
        });
        descripcion.setPromptText(destino instanceof EstadoDeuda d
                ? "Abono · " + d.deuda().nombre()
                : "¿En qué fue? (opcional)");
        actualizarGuardar();
    }

    private void actualizarGuardar() {
        if (modelo.seleccionadoProperty().get() != null) {
            guardar.setText("Guardar cambios");
            return;
        }
        Object destino = destino();
        guardar.setText(repetir.isSelected() ? "Crear recurrente"
                : destino instanceof EstadoDeuda ? "Registrar abono"
                : destino instanceof EstadoMeta ? "Aportar a la meta"
                : "Registrar gasto");
    }

    /** Lo gastado hoy: el contexto que importa al anotar el siguiente gasto. */
    private void pintarHoy() {
        var deHoy = modelo.gastosDeHoy();
        hoyTotal.setText(Formato.monto(deHoy.stream().map(Gasto::monto).reduce(BigDecimal.ZERO, BigDecimal::add)));
        hoyDetalle.setText(switch (deHoy.size()) {
            case 0 -> "Nada registrado aún";
            case 1 -> "1 gasto";
            default -> deHoy.size() + " gastos";
        });
    }

    void enfocar() {
        monto.requestFocus();
    }

    private void configurarCampos() {
        monto.getStyleClass().add("campo-monto");
        monto.setPromptText("0.00");
        descripcion.getStyleClass().add("campo");
        descripcion.setPromptText("¿En qué fue? (opcional)");
        fecha.setEditable(false); // solo calendario: evita fechas mal escritas
        fecha.setMaxWidth(Double.MAX_VALUE);
        fecha.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate valor) {
                return valor == null ? "" : valor.format(FECHA);
            }

            @Override
            public LocalDate fromString(String texto) {
                return texto == null || texto.isBlank() ? null : LocalDate.parse(texto, FECHA);
            }
        });
        monto.setOnAction(e -> guardar());
        descripcion.setOnAction(e -> guardar());
    }

    private static Node campo(String etiqueta, Node control) {
        return new VBox(8, Nodos.etiqueta(etiqueta, "campo-etiqueta"), control);
    }

    /** Llena el panel con un gasto (modo edición) o lo deja listo para uno nuevo. */
    private void cargar(Gasto gasto) {
        boolean editando = gasto != null;
        titulo.setText(editando ? "Editar gasto" : "Nuevo gasto");
        salirEdicion.setVisible(editando);
        eliminar.setVisible(editando);
        eliminar.setManaged(editando);
        eliminar.setText("Eliminar gasto");
        eliminar.getStyleClass().remove("boton-peligro-confirmar");
        limpiarErrores();
        // "Se repite" y el destino solo aplican a gastos nuevos
        repetir.setSelected(false);
        destinar.setSelected(false);
        cajaRepetir.setVisible(!editando);
        cajaRepetir.setManaged(!editando);
        pintarDestinos();
        actualizarGuardar();

        if (editando) {
            monto.setText(gasto.monto().toPlainString());
            descripcion.setText(gasto.descripcion());
            fecha.setValue(gasto.fecha());
            pintarChips(gasto.categoriaId());
        } else {
            monto.clear();
            descripcion.clear();
            fecha.setValue(modelo.hoy());
            pintarChips(null);
        }

        // Transición corta al cambiar de modo: el panel "respira" en vez de saltar
        var fundido = new FadeTransition(Duration.millis(160), contenido);
        fundido.setFromValue(0.35);
        fundido.setToValue(1);
        fundido.play();
    }

    private void pintarChips(String categoriaId) {
        categorias.getToggles().clear();
        chips.getChildren().setAll(chip("Sin categoría", null));
        modelo.resumen().categorias().forEach(c -> chips.getChildren().add(chip(c.nombre(), c)));
        categorias.getToggles().stream()
                .filter(t -> t.getUserData() instanceof Categoria c && c.id().equals(categoriaId))
                .findFirst()
                .orElse(categorias.getToggles().getFirst())
                .setSelected(true);
    }

    private ToggleButton chip(String texto, Categoria categoria) {
        var chip = new ToggleButton(texto, Nodos.punto(Paleta.colorDe(categoria)));
        chip.getStyleClass().add("chip");
        chip.setUserData(categoria);
        chip.setToggleGroup(categorias);
        return chip;
    }

    private String categoriaElegidaId() {
        var elegido = categorias.getSelectedToggle();
        return elegido != null && elegido.getUserData() instanceof Categoria c ? c.id() : null;
    }

    private void guardar() {
        limpiarErrores();
        BigDecimal valor;
        try {
            valor = Formato.leerMonto(monto.getText());
        } catch (IllegalArgumentException e) {
            marcarErrorMonto(e.getMessage());
            return;
        }

        var categoria = (Categoria) categorias.getSelectedToggle().getUserData();
        // La descripción es opcional: sin ella, el gasto se nombra como su categoría
        String texto = descripcion.getText().isBlank()
                ? (categoria == null ? "Gasto" : categoria.nombre())
                : descripcion.getText();
        try {
            Gasto editado = modelo.seleccionadoProperty().get();
            Object destino = editado == null ? destino() : null;
            if (destino instanceof EstadoMeta m) {
                modelo.aportar(m.meta().id(), valor, fecha.getValue());
                avisar(modelo.meta(m.meta().id())
                        .map(e -> e.completada()
                                ? "Meta completada: " + m.meta().nombre() + " llegó a su objetivo."
                                : "Aportaste " + Formato.monto(valor) + " a " + m.meta().nombre()
                                        + ". Faltan " + Formato.monto(e.restante()) + ".")
                        .orElse(""));
                limpiarCaptura();
            } else if (destino instanceof EstadoDeuda d) {
                modelo.abonarDeuda(d.deuda().id(), valor, fecha.getValue(), categoria, descripcion.getText());
                avisar(modelo.deuda(d.deuda().id())
                        .map(e -> e.saldada()
                                ? "Deuda saldada: " + d.deuda().nombre() + "."
                                : "Abono registrado. Saldo de " + d.deuda().nombre() + ": " + Formato.monto(e.saldo()) + ".")
                        .orElse(""));
                limpiarCaptura();
            } else if (editado == null && repetir.isSelected()) {
                // Crea la recurrencia; si el primer cobro es hoy o antes, ya queda registrado
                modelo.crearRecurrencia(texto, valor, fecha.getValue(), categoria,
                        frecuencia.cada(), frecuencia.unidad());
                monto.clear();
                descripcion.clear();
                repetir.setSelected(false);
                monto.requestFocus();
            } else if (editado == null) {
                modelo.registrar(texto, valor, fecha.getValue(), categoria);
                // Captura en ráfaga: se limpian monto y descripción, se conservan fecha y categoría
                monto.clear();
                descripcion.clear();
                monto.requestFocus();
            } else {
                modelo.editar(editado.id(), texto, valor, fecha.getValue(), categoria);
            }
        } catch (IllegalArgumentException e) {
            // Validaciones del dominio (p. ej. monto en cero o mayor al saldo): van junto al monto
            String mensaje = e.getMessage().toLowerCase();
            if (mensaje.contains("monto") || mensaje.contains("saldo")) {
                marcarErrorMonto(e.getMessage());
            } else {
                error.setText(e.getMessage());
            }
        }
    }

    /** Tras abonar o aportar: limpia como en la captura en ráfaga y apaga el destino. */
    private void limpiarCaptura() {
        monto.clear();
        descripcion.clear();
        destinar.setSelected(false);
        monto.requestFocus();
    }

    /** Confirmación breve: un aporte no aparece en Movimientos, así que hay que decir que pasó. */
    private void avisar(String texto) {
        aviso.setText(texto);
        apagarAviso.playFromStart();
    }

    private void marcarErrorMonto(String mensaje) {
        errorMonto.setText(mensaje);
        cajaMonto.getStyleClass().add("caja-monto-error");
        monto.requestFocus();
        monto.selectAll();
    }

    private void limpiarErrores() {
        error.setText("");
        aviso.setText("");
        errorMonto.setText("");
        cajaMonto.getStyleClass().remove("caja-monto-error");
    }

    /** Primer clic arma el botón, el segundo elimina. */
    private void eliminar() {
        if (!eliminar.getStyleClass().contains("boton-peligro-confirmar")) {
            eliminar.setText("Confirmar: eliminar definitivamente");
            eliminar.getStyleClass().add("boton-peligro-confirmar");
            return;
        }
        modelo.eliminar(modelo.seleccionadoProperty().get().id());
    }

    private void salirEdicion() {
        if (modelo.seleccionadoProperty().get() != null) {
            modelo.seleccionadoProperty().set(null);
        } else {
            cargar(null);
        }
    }
}
