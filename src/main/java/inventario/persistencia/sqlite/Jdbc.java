package inventario.persistencia.sqlite;

import inventario.persistencia.PersistenciaException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ayudante mínimo sobre JDBC: siempre usa PreparedStatement (nunca concatena valores en el SQL) y
 * convierte los tipos del dominio a los que guarda SQLite.
 */
final class Jdbc {

    /** Fechas como texto ISO con segundos, así se ordenan y comparan bien como cadenas. */
    static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");

    @FunctionalInterface
    interface Mapeo<T> {
        T mapear(ResultSet rs) throws SQLException;
    }

    private Jdbc() {
    }

    static int actualizar(Connection conexion, String sql, Object... parametros) {
        try (PreparedStatement ps = preparar(conexion, sql, parametros)) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw error(e);
        }
    }

    /** Ejecuta un INSERT y devuelve la clave generada (columna INTEGER PRIMARY KEY). */
    static long insertar(Connection conexion, String sql, Object... parametros) {
        try (PreparedStatement ps = preparar(conexion, sql, parametros)) {
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw error(e);
        }
    }

    static <T> List<T> consultar(Connection conexion, String sql, Mapeo<T> mapeo, Object... parametros) {
        try (PreparedStatement ps = preparar(conexion, sql, parametros); ResultSet rs = ps.executeQuery()) {
            List<T> resultado = new ArrayList<>();
            while (rs.next()) {
                resultado.add(mapeo.mapear(rs));
            }
            return resultado;
        } catch (SQLException e) {
            throw error(e);
        }
    }

    static <T> Optional<T> consultarUno(Connection conexion, String sql, Mapeo<T> mapeo, Object... parametros) {
        return consultar(conexion, sql, mapeo, parametros).stream().findFirst();
    }

    static LocalDateTime fecha(String texto) {
        return LocalDateTime.parse(texto);
    }

    private static PreparedStatement preparar(Connection conexion, String sql, Object... parametros)
            throws SQLException {
        PreparedStatement ps = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        for (int i = 0; i < parametros.length; i++) {
            ps.setObject(i + 1, valorSql(parametros[i]));
        }
        return ps;
    }

    private static Object valorSql(Object valor) {
        return switch (valor) {
            case null -> null;
            case BigDecimal numero -> numero.toPlainString();
            case LocalDateTime fecha -> fecha.format(FORMATO_FECHA);
            case LocalDate fecha -> fecha.toString();
            case Boolean b -> b ? 1 : 0;
            case Enum<?> e -> e.name();
            default -> valor;
        };
    }

    private static PersistenciaException error(SQLException e) {
        return new PersistenciaException("Error de base de datos: " + e.getMessage(), e);
    }
}
