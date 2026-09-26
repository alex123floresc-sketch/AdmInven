package inventario.persistencia;

/** Error al leer o escribir los datos (archivo o base de datos). */
public class PersistenciaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PersistenciaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
