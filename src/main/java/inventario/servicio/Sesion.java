package inventario.servicio;

import inventario.modelo.Permiso;
import inventario.modelo.Usuario;

import java.util.Optional;

/**
 * Usuario que está usando la aplicación. Los servicios la consultan antes de cada operación protegida,
 * así los permisos se cumplen igual desde la ventana, la consola o las pruebas.
 */
public final class Sesion {

    private final boolean sinRestricciones;
    private Usuario usuario;

    private Sesion(boolean sinRestricciones) {
        this.sinRestricciones = sinRestricciones;
    }

    /** Sesión vacía: hasta que alguien inicie sesión solo se permite consultar. */
    public static Sesion nueva() {
        return new Sesion(false);
    }

    /** Para procesos internos (importaciones, datos de ejemplo, pruebas): todo permitido, usuario "sistema". */
    public static Sesion sinRestricciones() {
        return new Sesion(true);
    }

    public Optional<Usuario> usuario() {
        return Optional.ofNullable(usuario);
    }

    /** Nombre que queda registrado en los movimientos. */
    public String nombreUsuario() {
        if (usuario != null) {
            return usuario.nombreUsuario();
        }
        return sinRestricciones ? "sistema" : "";
    }

    public boolean puede(Permiso permiso) {
        return sinRestricciones || (usuario != null && usuario.rol().permite(permiso));
    }

    public void requerir(Permiso permiso) {
        if (!puede(permiso)) {
            throw new InventarioException(usuario == null
                    ? "Debe iniciar sesión para " + permiso.descripcion() + "."
                    : "Su rol (" + usuario.rol() + ") no permite " + permiso.descripcion() + ".");
        }
    }

    /** Solo {@link UsuarioServicio} abre y cierra sesiones, tras verificar la contraseña. */
    void iniciar(Usuario usuario) {
        this.usuario = usuario;
    }

    void cerrar() {
        this.usuario = null;
    }
}
