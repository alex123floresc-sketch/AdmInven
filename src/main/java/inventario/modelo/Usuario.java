package inventario.modelo;

import java.util.Locale;
import java.util.Objects;

/**
 * Persona que usa la aplicación.
 *
 * @param nombreUsuario identificador para iniciar sesión (minúsculas, sin espacios)
 * @param contrasena    hash de la contraseña (nunca la contraseña en texto plano)
 */
public record Usuario(String nombreUsuario, String nombreCompleto, Rol rol, String contrasena, boolean activo) {

    public Usuario {
        nombreUsuario = normalizarNombreUsuario(nombreUsuario);
        if (nombreCompleto == null || nombreCompleto.isBlank()) {
            throw new IllegalArgumentException("El nombre completo no puede estar vacío.");
        }
        nombreCompleto = nombreCompleto.strip();
        Objects.requireNonNull(rol, "rol");
        Objects.requireNonNull(contrasena, "contrasena");
    }

    public static String normalizarNombreUsuario(String nombre) {
        String limpio = nombre == null ? "" : nombre.strip().toLowerCase(Locale.ROOT);
        if (!limpio.matches("[a-z0-9._-]{3,30}")) {
            throw new IllegalArgumentException(
                    "El usuario debe tener de 3 a 30 caracteres: letras, números, punto, guion o guion bajo.");
        }
        return limpio;
    }

    public Usuario conRol(Rol nuevoRol) {
        return new Usuario(nombreUsuario, nombreCompleto, nuevoRol, contrasena, activo);
    }

    public Usuario conContrasena(String nuevoHash) {
        return new Usuario(nombreUsuario, nombreCompleto, rol, nuevoHash, activo);
    }

    public Usuario conActivo(boolean nuevoActivo) {
        return new Usuario(nombreUsuario, nombreCompleto, rol, contrasena, nuevoActivo);
    }
}
