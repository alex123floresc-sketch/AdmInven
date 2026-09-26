package inventario.persistencia.sqlite;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Actualiza el esquema paso a paso. Cada versión es un script {@code /db/V<n>.sql}; la versión aplicada se
 * guarda en {@code PRAGMA user_version}. Para cambiar el esquema se añade un script nuevo, nunca se edita uno
 * ya publicado.
 */
final class Migraciones {

    private Migraciones() {
    }

    static int versionActual(Connection conexion) throws SQLException {
        try (Statement st = conexion.createStatement(); ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            return rs.getInt(1);
        }
    }

    static void aplicar(Connection conexion) throws SQLException {
        int version = versionActual(conexion);
        String script;
        while ((script = leerScript(version + 1)) != null) {
            version++;
            conexion.setAutoCommit(false);
            try (Statement st = conexion.createStatement()) {
                for (String sentencia : script.split(";")) {
                    if (!sinComentarios(sentencia).isBlank()) {
                        st.execute(sentencia);
                    }
                }
                st.execute("PRAGMA user_version = " + version);
                conexion.commit();
            } catch (SQLException e) {
                conexion.rollback();
                throw new SQLException("Falló la migración V" + version + ": " + e.getMessage(), e);
            } finally {
                conexion.setAutoCommit(true);
            }
        }
    }

    private static String leerScript(int version) {
        try (InputStream in = Migraciones.class.getResourceAsStream("/db/V" + version + ".sql")) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sinComentarios(String sentencia) {
        return sentencia.replaceAll("(?m)--.*$", "");
    }
}
