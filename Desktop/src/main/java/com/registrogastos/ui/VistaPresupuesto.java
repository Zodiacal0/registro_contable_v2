package com.registrogastos.ui;

import com.registrogastos.dominio.LineaPlan;
import com.registrogastos.servicio.PlanMes;
import javafx.animation.Animation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Presupuesto: tú armas la lista de lo que normalmente gastas al mes; el plan dice qué parte
 * del sueldo se lleva cada cosa y cuánto queda disponible; después asignas ese disponible
 * (ahorro, ocio, imprevistos...) hasta que no quede nada sin destino.
 */
final class VistaPresupuesto extends ScrollPane implements Revelable {

    // El color sigue al grupo: gastos en violeta medio, asignaciones en lila claro
    private static final String C_GASTOS = "#8a7aa8";
    private static final String C_ASIGNADO = "#cbbfe0";

    private final ModeloUi modelo;
    private final VBox contenido = new VBox(48);
    private Animation revelando;
    /** Qué quincena se muestra en "Cómo va el mes"; null = la que toca hoy. Se conserva al repintar. */
    private LineaPlan.Quincena quincenaVista;

    VistaPresupuesto(ModeloUi modelo) {
        this.modelo = modelo;
        contenido.getStyleClass().add("contenido");
        setContent(contenido);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        getStyleClass().add("vista");
        DesplazamientoSuave.aplicar(this);
        modelo.resumenProperty().addListener((obs, anterior, resumen) -> pintar());
        pintar();
    }

    @Override
    public void revelar() {
        if (revelando != null) {
            revelando.stop();
        }
        revelando = Movimiento.revelar(List.copyOf(contenido.getChildren()));
    }

    private void pintar() {
        PlanMes p = modelo.plan();
        var secciones = new ArrayList<Node>(List.of(encabezado(p), hero(p)));
        if (p.quincenal()) {
            secciones.add(comoVa(p));
            secciones.add(quincenas(p));
        }
        secciones.add(grupo(p, LineaPlan.Grupo.GASTO));
        secciones.add(grupo(p, LineaPlan.Grupo.ASIGNACION));
        contenido.getChildren().setAll(secciones);
    }

    // ---- Encabezado y hero ----

    private Node encabezado(PlanMes p) {
        var navegacion = new HBox(8);
        if (!modelo.enMesActual()) {
            navegacion.getChildren().add(Nodos.boton("Hoy", modelo::irAMesActual, "boton-pildora"));
        }
        navegacion.getChildren().addAll(
                Iconos.ANTERIOR.boton("Mes anterior", modelo::mesAnterior),
                Iconos.SIGUIENTE.boton("Mes siguiente", modelo::mesSiguiente));
        navegacion.setAlignment(Pos.CENTER);
        var fila = new HBox(10, Nodos.etiqueta("Presupuesto", "titulo"),
                Nodos.etiqueta(Formato.tituloMes(p.mes()) + " " + p.mes().getYear(), "titulo", "titulo-tenue"),
                Nodos.espaciador(), navegacion);
        fila.setAlignment(Pos.BASELINE_LEFT);
        fila.getStyleClass().add("encabezado");
        return fila;
    }

    /** Izquierda: lo disponible y el sueldo. Derecha: la barra sueldo → gastos → asignado → sin asignar. */
    private Node hero(PlanMes p) {
        BigDecimal disponible = p.disponible();
        String[] partes = Formato.montoPartido(disponible.abs());
        var cifra = new HBox(Nodos.etiqueta((disponible.signum() < 0 ? "−" : "") + partes[0], "saldo-entero"),
                Nodos.etiqueta(partes[1], "saldo-decimales"));
        cifra.setAlignment(Pos.BASELINE_LEFT);
        String titulo = !p.hayIngreso() ? "Sin sueldo para repartir"
                : disponible.signum() < 0 ? "Los gastos se pasan del sueldo por" : "Disponible al mes";
        var contexto = Nodos.etiqueta(p.hayIngreso()
                ? "Gastos " + Formato.monto(p.totalGastos()) + " (" + Formato.porcentaje(p.parte(p.totalGastos()))
                        + ")  ·  sueldo " + Formato.monto(p.ingresoEsperado())
                : "Escribe tu sueldo o registra un ingreso en Cuentas", "etiqueta");
        var izquierda = new VBox(10, Nodos.etiqueta(titulo, "etiqueta"), cifra, contexto, sueldo(p));
        izquierda.setMinWidth(300);
        izquierda.setPrefWidth(320);
        izquierda.setMaxWidth(320);

        var derecha = new VBox(16, Nodos.etiqueta("Así se va el sueldo", "seccion-titulo"), barra(p), leyenda(p),
                Nodos.etiqueta("Gastado de verdad este mes: " + Formato.monto(p.gastadoReal())
                        + (p.hayIngreso() ? "  ·  " + Formato.porcentaje(p.parte(p.gastadoReal())) + " del sueldo" : ""),
                        "leyenda-detalle"));
        HBox.setHgrow(derecha, Priority.ALWAYS);
        var hero = new HBox(48, izquierda, derecha);
        hero.setAlignment(Pos.TOP_LEFT);
        hero.getStyleClass().add("hero");
        return hero;
    }

    private Node sueldo(PlanMes p) {
        var campo = Campos.texto(modelo.ingresoMensual().map(BigDecimal::toPlainString).orElse(""), "Lo registrado");
        campo.getStyleClass().addAll("campo-chico", "campo-cifra");
        campo.setPrefWidth(140);
        var error = VistaListaDetalle.etiquetaError();
        guardarAlSalir(campo, texto -> modelo.fijarIngresoMensual(texto.isBlank() ? null : Formato.leerMonto(texto)), error);
        var fila = new HBox(10, Nodos.etiqueta("Sueldo al mes", "campo-etiqueta"), campo);
        fila.setAlignment(Pos.CENTER_LEFT);
        var ayuda = Nodos.etiqueta(p.ingresoFijado()
                ? "Registrado en Cuentas este mes: " + Formato.monto(p.ingresoRegistrado())
                : "Vacío: se usa lo registrado en Cuentas (" + Formato.monto(p.ingresoRegistrado()) + ")", "leyenda-detalle");
        ayuda.setWrapText(true);
        ayuda.setMinHeight(Region.USE_PREF_SIZE);

        var quincenal = Campos.interruptor("Me pagan por quincena");
        quincenal.setSelected(p.quincenal());
        quincenal.selectedProperty().addListener((obs, antes, ahora) -> modelo.fijarPagoQuincenal(ahora));
        var filaQuincenal = new HBox(10, Nodos.etiqueta("Me pagan por quincena", "interruptor-texto"), Nodos.espaciador(), quincenal);
        filaQuincenal.setAlignment(Pos.CENTER_LEFT);
        filaQuincenal.getStyleClass().add("caja-interruptor");
        filaQuincenal.setOnMouseClicked(e -> {
            if (e.getTarget() != quincenal) {
                quincenal.fire();
            }
        });
        return new VBox(10, new VBox(6, fila, error, ayuda), filaQuincenal);
    }

    /** Un tramo por cada línea de gasto, luego las asignaciones; lo que no se cubre es lo sin asignar. */
    private static Node barra(PlanMes p) {
        var barra = new HBox(2);
        barra.getStyleClass().add("distribucion");
        barra.setMinHeight(14);
        barra.setPrefHeight(14);
        for (LineaPlan l : p.gastos()) {
            barra.getChildren().add(tramo(barra, p.parte(l.monto()), C_GASTOS));
        }
        for (LineaPlan l : p.asignaciones()) {
            barra.getChildren().add(tramo(barra, p.parte(l.monto()), C_ASIGNADO));
        }
        if (p.hayIngreso() && p.sinAsignar().signum() > 0) {
            var resto = new Region();
            resto.getStyleClass().add("distribucion-libre");
            HBox.setHgrow(resto, Priority.ALWAYS);
            barra.getChildren().add(resto);
        }
        return barra;
    }

    private static Region tramo(HBox barra, double parte, String color) {
        var region = new Region();
        region.getStyleClass().add("distribucion-tramo");
        region.setStyle("-fx-background-color: " + color + ";");
        region.setMinWidth(3);
        region.prefWidthProperty().bind(barra.widthProperty().multiply(parte));
        return region;
    }

    private static Node leyenda(PlanMes p) {
        var flujo = new FlowPane(24, 12);
        flujo.getChildren().addAll(
                itemLeyenda("Gastos", p.totalGastos(), p, C_GASTOS),
                itemLeyenda("Asignado", p.totalAsignado(), p, C_ASIGNADO),
                itemLeyenda("Sin asignar", p.sinAsignar().max(BigDecimal.ZERO), p, null));
        return flujo;
    }

    private static Node itemLeyenda(String nombre, BigDecimal monto, PlanMes p, String color) {
        var muestra = new Region();
        muestra.getStyleClass().add(color == null ? "distribucion-libre-muestra" : "muestra");
        if (color != null) {
            muestra.setStyle("-fx-background-color: " + color + ";");
        }
        var item = new HBox(8, muestra, Nodos.etiqueta(nombre, "leyenda-nombre"), Nodos.etiqueta(Formato.monto(monto), "leyenda-monto"),
                Nodos.etiqueta(p.hayIngreso() ? Formato.porcentaje(p.parte(monto)) : "", "leyenda-detalle"));
        item.setAlignment(Pos.BASELINE_LEFT);
        return item;
    }

    // ---- Cómo va el mes (la quincena en curso, en grande) ----

    /** La quincena que toca hoy si es el mes en curso; si no, la primera. */
    private LineaPlan.Quincena quincenaDeHoy() {
        return modelo.enMesActual() && modelo.hoy().getDayOfMonth() > 15
                ? LineaPlan.Quincena.SEGUNDA : LineaPlan.Quincena.PRIMERA;
    }

    private Node comoVa(PlanMes p) {
        LineaPlan.Quincena q = quincenaVista != null ? quincenaVista : quincenaDeHoy();
        boolean enCurso = q == quincenaDeHoy() && modelo.enMesActual();

        // Selector 1ª | 2ª: cambia qué quincena se detalla, sin tocar los datos
        var grupo = new ToggleGroup();
        var selector = new HBox();
        selector.getStyleClass().add("segmentado");
        for (LineaPlan.Quincena cual : List.of(LineaPlan.Quincena.PRIMERA, LineaPlan.Quincena.SEGUNDA)) {
            var boton = new ToggleButton(cual == LineaPlan.Quincena.PRIMERA ? "1ª quincena" : "2ª quincena");
            boton.getStyleClass().add("segmento");
            boton.setUserData(cual);
            boton.setToggleGroup(grupo);
            boton.setSelected(cual == q);
            selector.getChildren().add(boton);
        }
        grupo.selectedToggleProperty().addListener((obs, anterior, nuevo) -> {
            if (nuevo == null) {
                anterior.setSelected(true);
                return;
            }
            quincenaVista = (LineaPlan.Quincena) nuevo.getUserData();
            pintar();
        });
        var titulo = new HBox(16, Nodos.etiqueta("Cómo va el mes", "seccion-titulo"), selector, Nodos.espaciador(),
                Nodos.etiqueta(enCurso ? "Estás en esta quincena" : "", "seccion-detalle"));
        titulo.setAlignment(Pos.CENTER_LEFT);

        // Izquierda: lo disponible de la quincena en grande y cómo va contra lo gastado de verdad
        BigDecimal disponible = p.disponibleEn(q);
        BigDecimal gastadoReal = p.gastadoRealEn(q);
        BigDecimal sinAsignar = p.sinAsignarEn(q);
        String[] partes = Formato.montoPartido(disponible.abs());
        var cifra = new HBox(Nodos.etiqueta((disponible.signum() < 0 ? "−" : "") + partes[0], "cifra-grande"),
                Nodos.etiqueta(partes[1], "cifra-media-decimales"));
        cifra.setAlignment(Pos.BASELINE_LEFT);
        var datos = new FlowPane(8, 8,
                VistaListaDetalle.dato("Sueldo " + Formato.monto(p.sueldoQuincena())),
                VistaListaDetalle.dato("Gastos del plan " + Formato.monto(p.gastosEn(q))),
                VistaListaDetalle.dato("Asignado " + Formato.monto(p.asignadoEn(q))),
                VistaListaDetalle.dato(sinAsignar.signum() < 0 ? "Asignado de más " + Formato.monto(sinAsignar.abs())
                        : "Sin asignar " + Formato.monto(sinAsignar)));
        var realTexto = Nodos.etiqueta("Gastado de verdad " + Formato.monto(gastadoReal)
                + (p.hayIngreso() ? "  ·  " + Formato.porcentaje(p.parteQuincena(gastadoReal)) + " del sueldo de la quincena" : ""),
                "leyenda-detalle");
        var realBarra = VistaListaDetalle.barraProgreso(p.parteQuincena(gastadoReal), "#e2dde7", true);
        var izquierda = new VBox(14, Nodos.etiqueta(disponible.signum() < 0 ? "Los gastos se pasan por" : "Disponible en la quincena", "etiqueta"),
                cifra, datos, new VBox(8, realTexto, realBarra));
        izquierda.setMinWidth(300);
        izquierda.setPrefWidth(380);
        izquierda.setMaxWidth(380);

        // Derecha: qué líneas caen en esta quincena
        var lista = new VBox(2);
        for (LineaPlan l : p.lineasEn(q, LineaPlan.Grupo.GASTO)) {
            lista.getChildren().add(filaQuincena(l, q, false));
        }
        for (LineaPlan l : p.lineasEn(q, LineaPlan.Grupo.ASIGNACION)) {
            lista.getChildren().add(filaQuincena(l, q, true));
        }
        if (lista.getChildren().isEmpty()) {
            lista.getChildren().add(VistaListaDetalle.vacio("Ninguna línea cae en esta quincena. Márcalas con 1ª, 2ª o mitad y mitad."));
        } else {
            lista.getStyleClass().add("tabla");
        }
        var derecha = new VBox(12, Nodos.etiqueta("Lo que toca en esta quincena", "seccion-detalle"), lista);
        HBox.setHgrow(derecha, Priority.ALWAYS);

        var cuerpo = new HBox(40, izquierda, derecha);
        cuerpo.setAlignment(Pos.TOP_LEFT);
        return new VBox(18, titulo, cuerpo);
    }

    private static Node filaQuincena(LineaPlan l, LineaPlan.Quincena q, boolean asignacion) {
        var muestra = new Region();
        muestra.getStyleClass().add("muestra");
        muestra.setStyle("-fx-background-color: " + (asignacion ? C_ASIGNADO : C_GASTOS) + ";");
        var fila = new HBox(10, muestra, Nodos.etiqueta(l.nombre(), "leyenda-nombre"),
                Nodos.etiqueta(l.quincena() == LineaPlan.Quincena.AMBAS ? "la mitad" : "", "leyenda-detalle"),
                Nodos.espaciador(), Nodos.etiqueta(Formato.monto(l.en(q)), "celda-monto"));
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().add("tabla-fila");
        return fila;
    }

    // ---- Por quincena ----

    /** Dos tarjetas, una por quincena: sueldo de la quincena, gastos, asignado y lo disponible. */
    private Node quincenas(PlanMes p) {
        var tarjetas = new HBox(12, tarjetaQuincena(p, LineaPlan.Quincena.PRIMERA, "1ª quincena"),
                tarjetaQuincena(p, LineaPlan.Quincena.SEGUNDA, "2ª quincena"));
        return new VBox(16, encabezado("Las dos quincenas", "La mitad del sueldo en cada una; cada línea cae donde la marques"), tarjetas);
    }

    private Node tarjetaQuincena(PlanMes p, LineaPlan.Quincena q, String titulo) {
        BigDecimal disponible = p.disponibleEn(q);
        BigDecimal sinAsignar = p.sinAsignarEn(q);
        var cifra = new HBox(8, Nodos.etiqueta(Formato.monto(disponible.abs()), "cifra-media"),
                Nodos.etiqueta(disponible.signum() < 0 ? "de más" : "disponible", "subtitulo"));
        cifra.setAlignment(Pos.BASELINE_LEFT);

        var barra = new HBox(2);
        barra.getStyleClass().add("distribucion");
        barra.setMinHeight(10);
        barra.setPrefHeight(10);
        barra.getChildren().add(tramo(barra, p.parteQuincena(p.gastosEn(q)), C_GASTOS));
        barra.getChildren().add(tramo(barra, p.parteQuincena(p.asignadoEn(q)), C_ASIGNADO));
        if (sinAsignar.signum() > 0) {
            var resto = new Region();
            resto.getStyleClass().add("distribucion-libre");
            HBox.setHgrow(resto, Priority.ALWAYS);
            barra.getChildren().add(resto);
        }

        var datos = new FlowPane(8, 8,
                VistaListaDetalle.dato("Sueldo " + Formato.monto(p.sueldoQuincena())),
                VistaListaDetalle.dato("Gastos " + Formato.monto(p.gastosEn(q))),
                VistaListaDetalle.dato("Asignado " + Formato.monto(p.asignadoEn(q))),
                VistaListaDetalle.dato(sinAsignar.signum() < 0 ? "Asignado de más " + Formato.monto(sinAsignar.abs())
                        : "Sin asignar " + Formato.monto(sinAsignar)));
        var tarjeta = new VBox(12, Nodos.etiqueta(titulo, "seccion-titulo"), cifra, barra, datos);
        tarjeta.getStyleClass().add("cobro-tarjeta");
        tarjeta.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(tarjeta, Priority.ALWAYS);
        return tarjeta;
    }

    /** Segmentado chico "1ª | 2ª | Mitad y mitad" para elegir en qué quincena cae la línea. */
    private Node selectorQuincena(LineaPlan l) {
        var grupo = new ToggleGroup();
        var fila = new HBox();
        fila.getStyleClass().addAll("segmentado", "segmentado-chico");
        for (LineaPlan.Quincena q : LineaPlan.Quincena.values()) {
            var boton = new ToggleButton(q.nombre());
            boton.getStyleClass().addAll("segmento", "segmento-chico");
            boton.setUserData(q);
            boton.setToggleGroup(grupo);
            boton.setSelected(q == l.quincena());
            fila.getChildren().add(boton);
        }
        grupo.selectedToggleProperty().addListener((obs, anterior, nuevo) -> {
            if (nuevo == null) {
                anterior.setSelected(true);
                return;
            }
            var elegida = (LineaPlan.Quincena) nuevo.getUserData();
            if (elegida != l.quincena()) {
                modelo.cambiarQuincenaLineaPlan(l.id(), elegida);
            }
        });
        return fila;
    }

    // ---- Los dos grupos ----

    /** Lista de líneas (monto editable, ✕ para quitar) y un renglón para agregar otra. */
    private Node grupo(PlanMes p, LineaPlan.Grupo grupo) {
        boolean esGasto = grupo == LineaPlan.Grupo.GASTO;
        List<LineaPlan> lineas = esGasto ? p.gastos() : p.asignaciones();
        String titulo = esGasto ? "Lo que normalmente gasto" : "Lo disponible lo asigno a";
        String detalle;
        if (esGasto) {
            detalle = lineas.isEmpty() ? "Renta, luz, comida, transporte..."
                    : Formato.monto(p.totalGastos()) + (p.hayIngreso() ? "  ·  " + Formato.porcentaje(p.parte(p.totalGastos())) + " del sueldo" : "");
        } else {
            BigDecimal resto = p.sinAsignar();
            detalle = resto.signum() < 0 ? "Asignaste " + Formato.monto(resto.abs()) + " de más"
                    : resto.signum() == 0 && !lineas.isEmpty() ? "Todo asignado"
                    : "Quedan " + Formato.monto(resto) + " sin asignar";
        }

        var filas = new VBox(2);
        for (LineaPlan l : lineas) {
            filas.getChildren().add(fila(l, p));
        }
        filas.getStyleClass().add("tabla");
        var secciones = new ArrayList<Node>(List.of(encabezado(titulo, detalle)));
        if (!lineas.isEmpty()) {
            secciones.add(filas);
        } else if (!esGasto) {
            secciones.add(VistaListaDetalle.vacio("Ahorro, un fondo para imprevistos, salidas... lo que quieras hacer con lo que queda."));
        }
        secciones.add(agregar(p, grupo));
        return new VBox(16, secciones.toArray(Node[]::new));
    }

    private Node fila(LineaPlan l, PlanMes p) {
        var monto = Campos.texto(l.monto().toPlainString(), "0.00");
        monto.getStyleClass().addAll("campo-chico", "campo-cifra", "campo-tope");
        var error = VistaListaDetalle.etiquetaError();
        guardarAlSalir(monto, texto -> modelo.editarLineaPlan(l.id(), l.nombre(), Formato.leerMonto(texto)), error);
        var quitar = Iconos.CERRAR.boton("Quitar", () -> modelo.eliminarLineaPlan(l.id()));
        quitar.getStyleClass().addAll("boton-icono-chico", "fila-quitar");

        var porcentaje = Nodos.etiqueta(p.hayIngreso() ? Formato.porcentaje(p.parte(l.monto())) : "", "leyenda-detalle");
        porcentaje.setMinWidth(44);
        porcentaje.setAlignment(Pos.CENTER_RIGHT);
        var barraLinea = (Region) VistaListaDetalle.barraProgreso(p.parte(l.monto()),
                l.grupo() == LineaPlan.Grupo.GASTO ? C_GASTOS : C_ASIGNADO, false);
        barraLinea.setMinWidth(120);
        barraLinea.setMaxWidth(160);
        HBox.setHgrow(barraLinea, Priority.ALWAYS);

        var nombre = Nodos.etiqueta(l.nombre(), "leyenda-nombre");
        nombre.setMinWidth(140);
        var fila = new HBox(14, nombre, barraLinea, porcentaje, Nodos.espaciador());
        if (p.quincenal()) {
            fila.getChildren().add(selectorQuincena(l));
        }
        fila.getChildren().addAll(monto, quitar);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().add("tabla-fila");
        return new VBox(4, fila, error);
    }

    /** Nombre + monto + Agregar. En asignaciones, "El resto" rellena con lo que queda sin asignar. */
    private Node agregar(PlanMes p, LineaPlan.Grupo grupo) {
        boolean esGasto = grupo == LineaPlan.Grupo.GASTO;
        TextField nombre = Campos.texto("", esGasto ? "Ej. Renta" : "Ej. Ahorro");
        TextField monto = Campos.monto(null);
        monto.getStyleClass().add("campo-cifra");
        monto.setPrefWidth(130);
        var error = VistaListaDetalle.etiquetaError();
        Runnable agregar = () -> VistaListaDetalle.ejecutar(error, () -> {
            modelo.agregarLineaPlan(nombre.getText(), Formato.leerMonto(monto.getText()), grupo);
        });
        nombre.setOnAction(e -> monto.requestFocus());
        monto.setOnAction(e -> agregar.run());

        var botones = new HBox(4, Nodos.boton("Agregar", agregar, "boton-pildora"));
        if (!esGasto && p.sinAsignar().signum() > 0) {
            botones.getChildren().add(Nodos.boton("El resto (" + Formato.monto(p.sinAsignar()) + ")",
                    () -> monto.setText(p.sinAsignar().toPlainString()), "boton-texto"));
        }
        if (esGasto && !p.sugeridas().isEmpty()) {
            int n = p.sugeridas().size();
            botones.getChildren().add(Nodos.boton("Traer " + (n == 1 ? "mi recurrente" : "mis " + n + " recurrentes"),
                    modelo::traerRecurrentesAlPlan, "boton-texto"));
        }
        botones.setAlignment(Pos.CENTER_LEFT);

        var campos = new HBox(10, nombre, monto);
        HBox.setHgrow(nombre, Priority.ALWAYS);
        var caja = new VBox(10, campos, error, botones);
        caja.getStyleClass().add("plan-agregar");
        return caja;
    }

    /** Guarda con Enter o al salir del campo; si el valor no cambió, no recarga nada. */
    private static void guardarAlSalir(TextField campo, Consumer<String> guardar, Label error) {
        var inicial = campo.getText();
        Runnable intentar = () -> {
            if (campo.getText().strip().equals(inicial.strip())) {
                return;
            }
            VistaListaDetalle.ejecutar(error, () -> guardar.accept(campo.getText()));
        };
        campo.setOnAction(e -> intentar.run());
        campo.focusedProperty().addListener((obs, antes, enfocado) -> {
            if (!enfocado) {
                intentar.run();
            }
        });
    }

    private static Node encabezado(String titulo, String detalle) {
        var fila = new HBox(Nodos.etiqueta(titulo, "seccion-titulo"), Nodos.espaciador(),
                Nodos.etiqueta(detalle, "seccion-detalle"));
        fila.setAlignment(Pos.BASELINE_LEFT);
        return fila;
    }
}
