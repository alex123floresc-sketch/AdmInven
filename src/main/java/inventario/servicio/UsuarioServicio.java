package inventario.servicio;

import inventario.modelo.Permiso;
import inventario.modelo.Rol;
import inventario.modelo.Usuario;
import inventario.persistencia.UsuarioRepositorio;

import java.util.List;
import java.util.Optional;

/** Inicio de sesión y administración de usuarios. */
public class UsuarioServicio {

    private static final String CREDENCIALES_INVALIDAS = "Usuario o contraseña incorrectos.";

    private final UsuarioRepositorio usuarios;
    private final Sesion sesion;

    public UsuarioServicio(UsuarioRepositorio usuarios, Sesion sesion) {
        this.usuarios = usuarios;
        this.sesion = sesion;
    }

    public Sesion sesion() {
        return sesion;
    }

    // ---- Sesión ----

    /** Al terminar, la contraseña recibida se borra de la memoria. */
    public Usuario iniciarSesion(String nombreUsuario, char[] contrasena) {
        try {
            // Mismo mensaje si el usuario no existe o la contraseña falla: no se revela qué usuarios existen.
            Usuario u = buscar(nombreUsuario)
                    .filter(Usuario::activo)
                    .filter(x -> Contrasenas.verificar(contrasena, x.contrasena()))
                    .orElseThrow(() -> new InventarioException(CREDENCIALES_INVALIDAS));
            sesion.iniciar(u);
            return u;
        } finally {
            Contrasenas.borrar(contrasena);
        }
    }

    public void cerrarSesion() {
        sesion.cerrar();
    }

    /** Primera ejecución: todavía no hay usuarios y hay que crear el administrador. */
    public boolean requiereConfiguracionInicial() {
        return usuarios.listar().isEmpty();
    }

    /** Crea el primer administrador; solo funciona si aún no hay ningún usuario. */
    public Usuario crearAdministradorInicial(String nombreUsuario, String nombreCompleto, char[] contrasena) {
        if (!requiereConfiguracionInicial()) {
            Contrasenas.borrar(contrasena);
            throw new InventarioException("Ya hay usuarios registrados; inicie sesión como administrador.");
        }
        Usuario admin = crear(nombreUsuario, nombreCompleto, Rol.ADMINISTRADOR, contrasena);
        sesion.iniciar(admin);
        return admin;
    }

    public void cambiarMiContrasena(char[] actual, char[] nueva) {
        Usuario yo = sesion.usuario().orElseThrow(() -> new InventarioException("Debe iniciar sesión."));
        try {
            if (!Contrasenas.verificar(actual, yo.contrasena())) {
                throw new InventarioException("La contraseña actual no es correcta.");
            }
            validarContrasena(nueva);
            Usuario actualizado = yo.conContrasena(Contrasenas.hashear(nueva));
            guardar(actualizado);
            sesion.iniciar(actualizado);
        } finally {
            Contrasenas.borrar(actual);
            Contrasenas.borrar(nueva);
        }
    }

    // ---- Administración (solo administradores) ----

    public List<Usuario> listar() {
        sesion.requerir(Permiso.GESTIONAR_USUARIOS);
        return usuarios.listar();
    }

    public Usuario crearUsuario(String nombreUsuario, String nombreCompleto, Rol rol, char[] contrasena) {
        try {
            sesion.requerir(Permiso.GESTIONAR_USUARIOS);
        } catch (InventarioException e) {
            Contrasenas.borrar(contrasena);
            throw e;
        }
        return crear(nombreUsuario, nombreCompleto, rol, contrasena);
    }

    public void cambiarRol(String nombreUsuario, Rol rol) {
        sesion.requerir(Permiso.GESTIONAR_USUARIOS);
        Usuario u = obtener(nombreUsuario);
        if (u.rol() == Rol.ADMINISTRADOR && rol != Rol.ADMINISTRADOR) {
            protegerUltimoAdministrador(u);
        }
        guardar(u.conRol(rol));
    }

    public void restablecerContrasena(String nombreUsuario, char[] nueva) {
        try {
            sesion.requerir(Permiso.GESTIONAR_USUARIOS);
            validarContrasena(nueva);
            guardar(obtener(nombreUsuario).conContrasena(Contrasenas.hashear(nueva)));
        } finally {
            Contrasenas.borrar(nueva);
        }
    }

    public void desactivar(String nombreUsuario) {
        sesion.requerir(Permiso.GESTIONAR_USUARIOS);
        Usuario u = obtener(nombreUsuario);
        if (u.nombreUsuario().equals(sesion.nombreUsuario())) {
            throw new InventarioException("No puede desactivar su propio usuario.");
        }
        if (u.rol() == Rol.ADMINISTRADOR) {
            protegerUltimoAdministrador(u);
        }
        guardar(u.conActivo(false));
    }

    public void activar(String nombreUsuario) {
        sesion.requerir(Permiso.GESTIONAR_USUARIOS);
        guardar(obtener(nombreUsuario).conActivo(true));
    }

    // ---- Auxiliares ----

    private Usuario crear(String nombreUsuario, String nombreCompleto, Rol rol, char[] contrasena) {
        try {
            String nombre = normalizar(nombreUsuario);
            if (usuarios.buscar(nombre).isPresent()) {
                throw new InventarioException("Ya existe el usuario \"" + nombre + "\".");
            }
            validarContrasena(contrasena);
            Usuario nuevo = new Usuario(nombre, nombreCompleto, rol, Contrasenas.hashear(contrasena), true);
            guardar(nuevo);
            return nuevo;
        } catch (IllegalArgumentException e) {
            throw new InventarioException(e.getMessage());
        } finally {
            Contrasenas.borrar(contrasena);
        }
    }

    /** Siempre debe quedar al menos un administrador activo, o nadie podría gestionar usuarios. */
    private void protegerUltimoAdministrador(Usuario u) {
        long adminsActivos = usuarios.listar().stream()
                .filter(x -> x.activo() && x.rol() == Rol.ADMINISTRADOR).count();
        if (u.activo() && adminsActivos <= 1) {
            throw new InventarioException("Debe quedar al menos un administrador activo.");
        }
    }

    private static void validarContrasena(char[] contrasena) {
        String problema = Contrasenas.problema(contrasena);
        if (problema != null) {
            throw new InventarioException(problema);
        }
    }

    private Usuario obtener(String nombreUsuario) {
        return buscar(nombreUsuario)
                .orElseThrow(() -> new InventarioException("No existe el usuario \"" + nombreUsuario + "\"."));
    }

    private Optional<Usuario> buscar(String nombreUsuario) {
        try {
            return usuarios.buscar(Usuario.normalizarNombreUsuario(nombreUsuario));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String normalizar(String nombreUsuario) {
        try {
            return Usuario.normalizarNombreUsuario(nombreUsuario);
        } catch (IllegalArgumentException e) {
            throw new InventarioException(e.getMessage());
        }
    }

    private void guardar(Usuario usuario) {
        try {
            usuarios.guardar(usuario);
        } catch (RuntimeException e) {
            throw new InventarioException("No se pudo guardar el usuario: " + e.getMessage(), e);
        }
    }
}
