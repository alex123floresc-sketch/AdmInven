package inventario.persistencia.sqlite;

import inventario.modelo.Rol;
import inventario.modelo.Usuario;
import inventario.persistencia.UsuarioRepositorio;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class SqliteUsuarioRepositorio implements UsuarioRepositorio {

    private static final String COLUMNAS = "nombre_usuario, nombre_completo, rol, contrasena, activo";

    private final BaseDeDatos bd;

    public SqliteUsuarioRepositorio(BaseDeDatos bd) {
        this.bd = bd;
    }

    @Override
    public List<Usuario> listar() {
        return Jdbc.consultar(bd.conexion(), "SELECT " + COLUMNAS + " FROM usuario ORDER BY nombre_usuario",
                SqliteUsuarioRepositorio::mapear);
    }

    @Override
    public Optional<Usuario> buscar(String nombreUsuario) {
        return Jdbc.consultarUno(bd.conexion(), "SELECT " + COLUMNAS + " FROM usuario WHERE nombre_usuario = ?",
                SqliteUsuarioRepositorio::mapear, nombreUsuario);
    }

    @Override
    public void guardar(Usuario u) {
        Jdbc.actualizar(bd.conexion(), """
                        INSERT INTO usuario (%s) VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT (nombre_usuario) DO UPDATE SET
                            nombre_completo = excluded.nombre_completo, rol = excluded.rol,
                            contrasena = excluded.contrasena, activo = excluded.activo
                        """.formatted(COLUMNAS),
                u.nombreUsuario(), u.nombreCompleto(), u.rol(), u.contrasena(), u.activo());
    }

    private static Usuario mapear(ResultSet rs) throws SQLException {
        return new Usuario(rs.getString("nombre_usuario"), rs.getString("nombre_completo"),
                Rol.valueOf(rs.getString("rol")), rs.getString("contrasena"), rs.getInt("activo") == 1);
    }
}
