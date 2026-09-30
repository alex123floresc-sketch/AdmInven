package inventario.persistencia;

import java.nio.file.Path;
import java.util.Optional;

/** Copias de seguridad del almacenamiento completo (productos, movimientos, proveedores y usuarios). */
public interface Respaldos {

    /**
     * Escribe una copia coherente de todos los datos en {@code destino}, reemplazándolo si ya existe.
     * Si falla, el archivo anterior queda intacto.
     */
    void copiarEn(Path destino);

    /** Archivo con los datos en uso, si el almacenamiento vive en disco. */
    Optional<Path> archivo();
}
