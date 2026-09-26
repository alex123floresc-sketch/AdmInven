package inventario.modelo;

import java.util.Objects;

/**
 * Empresa o persona a la que se compra mercadería.
 *
 * @param id        asignado por el almacenamiento; {@code null} si aún no se guardó
 * @param documento RUC (11 dígitos) o DNI (8 dígitos); puede estar vacío
 */
public record Proveedor(Long id, String nombre, String documento, String telefono, String email, boolean activo) {

    public Proveedor {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre del proveedor no puede estar vacío.");
        }
        nombre = nombre.strip();
        documento = limpiar(documento);
        telefono = limpiar(telefono);
        email = limpiar(email);
        if (!documento.isEmpty() && !documento.matches("\\d{8}|\\d{11}")) {
            throw new IllegalArgumentException("El documento debe ser un RUC de 11 dígitos o un DNI de 8.");
        }
        if (!email.isEmpty() && !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new IllegalArgumentException("El correo electrónico no es válido.");
        }
    }

    public Proveedor conId(long nuevoId) {
        return new Proveedor(nuevoId, nombre, documento, telefono, email, activo);
    }

    public Proveedor conActivo(boolean nuevoActivo) {
        return new Proveedor(id, nombre, documento, telefono, email, nuevoActivo);
    }

    @Override
    public String toString() {
        return nombre;
    }

    private static String limpiar(String texto) {
        return Objects.requireNonNullElse(texto, "").strip();
    }
}
