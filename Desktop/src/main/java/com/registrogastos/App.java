package com.registrogastos;

import com.registrogastos.consola.MenuConsola;
import com.registrogastos.persistencia.BaseDeDatos;
import com.registrogastos.persistencia.CategoriaRepositorio;
import com.registrogastos.persistencia.GastoRepositorio;
import com.registrogastos.persistencia.Migrador;
import com.registrogastos.servicio.GastoServicio;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Raíz de composición: el único lugar que hace "new" de las piezas y las conecta.
 * El resto de clases reciben sus dependencias por constructor.
 */
public final class App {

    public static void main(String[] args) {
        // -Dgastos.db=ruta permite usar otra base (p. ej. una de pruebas)
        Path archivo = Path.of(System.getProperty("gastos.db",
                Path.of(System.getProperty("user.home"), ".registro-gastos", "gastos.db").toString()));

        var baseDeDatos = new BaseDeDatos(archivo);
        var migrador = new Migrador(baseDeDatos);
        migrador.migrar();

        var servicio = new GastoServicio(
                new GastoRepositorio(baseDeDatos),
                new CategoriaRepositorio(baseDeDatos),
                Clock.systemDefaultZone());

        IO.println("Base: " + baseDeDatos.archivo() + " (esquema v" + migrador.versionActual() + ")");
        new MenuConsola(servicio).iniciar();
    }
}
