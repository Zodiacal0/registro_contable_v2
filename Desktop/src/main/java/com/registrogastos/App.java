package com.registrogastos;

import com.registrogastos.persistencia.BaseDeDatos;
import com.registrogastos.persistencia.CategoriaRepositorio;
import com.registrogastos.persistencia.GastoRepositorio;
import com.registrogastos.persistencia.Migrador;
import com.registrogastos.persistencia.RecurrenciaRepositorio;
import com.registrogastos.servicio.GastoServicio;
import com.registrogastos.servicio.RecurrenciaServicio;
import com.registrogastos.ui.ModeloUi;
import com.registrogastos.ui.VentanaPrincipal;
import javafx.application.Application;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Raíz de composición: el único lugar que hace "new" de las piezas y las conecta.
 *
 * Ciclo de vida JavaFX: launch() → init() (hilo del launcher) → start() (hilo de JavaFX).
 * Todo lo que toca nodos debe ocurrir en el hilo de JavaFX, por eso la UI se arma en start().
 */
public final class App extends Application {

    /**
     * La barra de título propia (StageStyle.EXTENDED + HeaderBar) es una función "preview" de
     * JavaFX 25 y hay que activarla ANTES de que arranque el toolkit de JavaFX.
     *
     * Ojo: si la clase principal extiende Application, el lanzador de Java la arranca sin
     * llamar a main() y sin inicializarla hasta después del toolkit, así que ni main() ni un
     * bloque static llegan a tiempo. Por eso ejecutar.cmd pasa -Djavafx.enablePreview=true.
     * Este método sirve para los main() que sí corren primero (Demo, o lanzar con otra clase).
     * Al subir de versión de JavaFX, revisar si ya es estable (y quitar esto) o si cambió.
     */
    static void activarPreview() {
        System.setProperty("javafx.enablePreview", "true");
        System.setProperty("javafx.suppressPreviewWarning", "true");
    }

    /** Los servicios de la app, ya conectados a la misma base. */
    record Servicios(GastoServicio gastos, RecurrenciaServicio recurrencias) {
    }

    private Servicios servicios;

    @Override
    public void init() {
        // Trabajo sin UI (abrir y migrar la base) fuera del hilo de JavaFX
        servicios = crearServicios(rutaBase());
    }

    @Override
    public void start(Stage escenario) {
        VentanaPrincipal.cargarFuentes();
        new VentanaPrincipal(new ModeloUi(servicios.gastos(), servicios.recurrencias())).mostrar(escenario);
    }

    /** -Dgastos.db=ruta permite usar otra base (p. ej. una de pruebas). */
    static Path rutaBase() {
        return Path.of(System.getProperty("gastos.db",
                Path.of(System.getProperty("user.home"), ".registro-gastos", "gastos.db").toString()));
    }

    static Servicios crearServicios(Path archivo) {
        var baseDeDatos = new BaseDeDatos(archivo);
        new Migrador(baseDeDatos).migrar();
        var gastos = new GastoRepositorio(baseDeDatos);
        var categorias = new CategoriaRepositorio(baseDeDatos);
        var reloj = Clock.systemDefaultZone();
        return new Servicios(
                new GastoServicio(gastos, categorias, reloj),
                new RecurrenciaServicio(new RecurrenciaRepositorio(baseDeDatos), gastos, categorias, reloj));
    }

    public static void main(String[] args) {
        activarPreview();
        launch(args);
    }
}
