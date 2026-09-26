package inventario.persistencia;

import inventario.modelo.Usuario;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepositorio {

    /** Todos los usuarios, ordenados por nombre de usuario. */
    List<Usuario> listar();

    Optional<Usuario> buscar(String nombreUsuario);

    /** Inserta o actualiza según el nombre de usuario. */
    void guardar(Usuario usuario);
}
