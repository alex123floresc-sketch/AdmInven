package inventario.servicio;

/**
 * Error de negocio con un mensaje apto para mostrar al usuario.
 */
public class InventarioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InventarioException(String mensaje) {
        super(mensaje);
    }

    public InventarioException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
