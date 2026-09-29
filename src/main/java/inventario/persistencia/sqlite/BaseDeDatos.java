package inventario.persistencia.sqlite;

import inventario.persistencia.PersistenciaException;
import inventario.persistencia.Transacciones;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Supplier;

/**
 * Conexión única a la base de datos SQLite de la aplicación. La conexión no admite operaciones simultáneas:
 * el servidor web atiende las peticiones de una en una (ver {@code ServidorWeb}).
 * Al abrirla aplica las migraciones pendientes, así el esquema siempre está al día.
 */
public final class BaseDeDatos implements Transacciones, AutoCloseable {

    private final Connection conexion;

    private BaseDeDatos(String url) {
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
        try {
            Files.createDirectories(archivo.toAbsolutePath().getParent());
        } catch (IOException e) {
            throw new PersistenciaException("No se pudo crear la carpeta de datos: " + e.getMessage(), e);
        }
        return new BaseDeDatos("jdbc:sqlite:" + archivo.toAbsolutePath());
    }

    /** Base de datos temporal que desaparece al cerrarse; útil para pruebas. */
    public static BaseDeDatos enMemoria() {
        return new BaseDeDatos("jdbc:sqlite::memory:");
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
