package inventario.servicio;

import inventario.modelo.Permiso;
import inventario.persistencia.Respaldos;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Copias de seguridad de todos los datos en un solo archivo. Para restaurar una copia basta con cerrar el
 * programa y reemplazar {@code inventario.db} de la carpeta de datos por el archivo copiado.
 */
public final class RespaldoServicio {

    private static final DateTimeFormatter FORMATO_NOMBRE = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm");

    private final Respaldos respaldos;
    private final Clock reloj;
    private final Sesion sesion;

    public RespaldoServicio(Respaldos respaldos, Clock reloj, Sesion sesion) {
        this.respaldos = respaldos;
        this.reloj = reloj;
        this.sesion = sesion;
    }

    /** Nombre propuesto para el archivo, con la fecha y la hora: {@code inventario-respaldo-2026-09-29-1530.db}. */
    public String nombreSugerido() {
        return "inventario-respaldo-" + LocalDateTime.now(reloj).format(FORMATO_NOMBRE) + ".db";
    }

    /** Guarda la copia en {@code destino}; si el archivo existe se reemplaza. */
    public void crearCopia(Path destino) {
        sesion.requerir(Permiso.RESPALDAR_DATOS);
        if (destino == null) {
            throw new InventarioException("Elija dónde guardar la copia de seguridad.");
        }
        Path elegido = destino.toAbsolutePath().normalize();
        if (respaldos.archivo().filter(elegido::equals).isPresent()) {
            throw new InventarioException("La copia no puede reemplazar la base de datos en uso; elija otro archivo.");
        }
        try {
            respaldos.copiarEn(elegido);
        } catch (RuntimeException e) {
            throw new InventarioException(e.getMessage(), e);
        }
    }
}
