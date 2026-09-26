package inventario.persistencia.sqlite;

import inventario.modelo.Proveedor;
import inventario.persistencia.ProveedorRepositorio;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class SqliteProveedorRepositorio implements ProveedorRepositorio {

    private static final String COLUMNAS = "id, nombre, documento, telefono, email, activo";

    private final BaseDeDatos bd;

    public SqliteProveedorRepositorio(BaseDeDatos bd) {
        this.bd = bd;
    }

    @Override
    public List<Proveedor> listar() {
        return Jdbc.consultar(bd.conexion(), "SELECT " + COLUMNAS + " FROM proveedor ORDER BY nombre COLLATE NOCASE",
                SqliteProveedorRepositorio::mapear);
    }

    @Override
    public Optional<Proveedor> buscarPorId(long id) {
        return Jdbc.consultarUno(bd.conexion(), "SELECT " + COLUMNAS + " FROM proveedor WHERE id = ?",
                SqliteProveedorRepositorio::mapear, id);
    }

    @Override
    public Proveedor guardar(Proveedor p) {
        if (p.id() == null) {
            long id = Jdbc.insertar(bd.conexion(),
                    "INSERT INTO proveedor (nombre, documento, telefono, email, activo) VALUES (?, ?, ?, ?, ?)",
                    p.nombre(), p.documento(), p.telefono(), p.email(), p.activo());
            return p.conId(id);
        }
        Jdbc.actualizar(bd.conexion(),
                "UPDATE proveedor SET nombre = ?, documento = ?, telefono = ?, email = ?, activo = ? WHERE id = ?",
                p.nombre(), p.documento(), p.telefono(), p.email(), p.activo(), p.id());
        return p;
    }

    private static Proveedor mapear(ResultSet rs) throws SQLException {
        return new Proveedor(rs.getLong("id"), rs.getString("nombre"), rs.getString("documento"),
                rs.getString("telefono"), rs.getString("email"), rs.getInt("activo") == 1);
    }
}
