package inventario.persistencia.sqlite;

import inventario.persistencia.PersistenciaException;
import inventario.persistencia.Respaldos;
import inventario.persistencia.Transacciones;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Conexión única a la base de datos SQLite de la aplicación. La conexión no admite operaciones simultáneas:
 * el servidor web atiende las peticiones de una en una (ver {@code ServidorWeb}).
 * Al abrirla aplica las migraciones pendientes, así el esquema siempre está al día.
 */
public final class BaseDeDatos implements Transacciones, Respaldos, AutoCloseable {

    private final Connection conexion;
    /** {@code null} si la base vive en memoria. */
    private final Path archivo;

    private BaseDeDatos(String url, Path archivo) {
        this.archivo = archivo;
        try {
            conexion = DriverManager.getConnection(url);
            try (Statement st = conexion.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                // Si otro programa (p. ej. la ventana y el servidor a la vez) está escribiendo, espera en vez de fallar.
                st.execute("PRAGMA busy_timeout = 5000");
            }
            Migraciones.aplicar(conexion);
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo abrir la base de datos: " + e.getMessage(), e);
        }
    }

    public static BaseDeDatos abrir(Path archivo) {
        Path absoluto = archivo.toAbsolutePath().normalize();
        try {
            Files.createDirectories(absoluto.getParent());
        } catch (IOException e) {
            throw new PersistenciaException("No se pudo crear la carpeta de datos: " + e.getMessage(), e);
        }
        return new BaseDeDatos("jdbc:sqlite:" + absoluto, absoluto);
    }

    /** Base de datos temporal que desaparece al cerrarse; útil para pruebas. */
    public static BaseDeDatos enMemoria() {
        return new BaseDeDatos("jdbc:sqlite::memory:", null);
    }

    @Override
    public Optional<Path> archivo() {
        return Optional.ofNullable(archivo);
    }

    /**
     * Usa {@code VACUUM INTO}: SQLite escribe una copia compacta y coherente aunque la base esté abierta.
     * Se escribe primero en un archivo temporal junto al destino y luego se renombra, así una copia a medias
     * nunca reemplaza a una buena.
     */
    @Override
    public void copiarEn(Path destino) {
        Path absoluto = destino.toAbsolutePath().normalize();
        Path temporal = absoluto.resolveSibling(absoluto.getFileName() + ".tmp");
        try {
            Files.createDirectories(absoluto.getParent());
            Files.deleteIfExists(temporal);
            try (PreparedStatement ps = conexion.prepareStatement("VACUUM INTO ?")) {
                ps.setString(1, temporal.toString());
                ps.execute();
            }
            Files.move(temporal, absoluto, StandardCopyOption.REPLACE_EXISTING);
        } catch (SQLException | IOException e) {
            try {
                Files.deleteIfExists(temporal);
            } catch (IOException ignorada) {
                e.addSuppressed(ignorada);
            }
            throw new PersistenciaException("No se pudo crear la copia de seguridad: " + e.getMessage(), e);
        }
    }

    Connection conexion() {
        return conexion;
    }

    public int versionEsquema() {
        try {
            return Migraciones.versionActual(conexion);
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo leer la versión del esquema", e);
        }
    }

    /** Si ya hay una transacción en curso, la operación se une a ella. */
    @Override
    public <T> T ejecutar(Supplier<T> operacion) {
        try {
            if (!conexion.getAutoCommit()) {
                return operacion.get();
            }
            conexion.setAutoCommit(false);
            try {
                T resultado = operacion.get();
                conexion.commit();
                return resultado;
            } catch (RuntimeException | Error e) {
                conexion.rollback();
                throw e;
            } finally {
                conexion.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Error en la transacción: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        try {
            conexion.close();
        } catch (SQLException e) {
            throw new PersistenciaException("No se pudo cerrar la base de datos", e);
        }
    }
}
