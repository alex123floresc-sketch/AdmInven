package inventario.servicio;

import inventario.modelo.Usuario;
import inventario.persistencia.UsuarioRepositorio;

import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/** Repositorio sin archivos, para probar los servicios de forma aislada. */
class UsuarioRepositorioEnMemoria implements UsuarioRepositorio {

    private final TreeMap<String, Usuario> usuarios = new TreeMap<>();

    @Override
    public List<Usuario> listar() {
        return List.copyOf(usuarios.values());
    }

    @Override
    public Optional<Usuario> buscar(String nombreUsuario) {
        return Optional.ofNullable(usuarios.get(nombreUsuario));
    }

    @Override
    public void guardar(Usuario usuario) {
        usuarios.put(usuario.nombreUsuario(), usuario);
    }
}
