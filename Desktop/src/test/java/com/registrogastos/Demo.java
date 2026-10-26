package com.registrogastos;

import javafx.application.Application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Herramienta de desarrollo: abre la app normal (interactiva) sobre una base temporal
 * con datos de ejemplo. Tus datos reales no se tocan.
 */
public final class Demo {

    public static void main(String[] args) throws IOException {
        App.activarPreview();
        Path base = Files.createTempDirectory("demo-gastos").resolve("demo.db");
        CapturaPantallas.sembrar(App.crearServicios(base));
        System.setProperty("gastos.db", base.toString()); // App.rutaBase() la lee en init()
        Application.launch(App.class, args);
    }
}
