package com.registrogastos.persistencia;

import org.sqlite.SQLiteConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Sabe DÓNDE está el archivo .db y CÓMO abrir conexiones configuradas.
 * Nadie más en el proyecto arma URLs JDBC.
 */
public final class BaseDeDatos {

    private final Path archivo;
    private final String url;

    public BaseDeDatos(Path archivo) {
        this.archivo = archivo.toAbsolutePath();
        this.url = "jdbc:sqlite:" + this.archivo;
        try {
            Files.createDirectories(this.archivo.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear la carpeta de datos", e);
        }
    }

    /** Quien la abre la cierra: úsala siempre con try-with-resources. */
    public Connection abrirConexion() throws SQLException {
        var config = new SQLiteConfig();
        config.enforceForeignKeys(true); // SQLite las trae APAGADAS por defecto
        return DriverManager.getConnection(url, config.toProperties());
    }

    public Path archivo() {
        return archivo;
    }
}
