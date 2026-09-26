package inventario.modelo;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Registro inmutable de un cambio de stock sobre un producto.
 */
public record Movimiento(
        LocalDateTime fecha,
        String codigoProducto,
        TipoMovimiento tipo,
        int cantidad,
        int stockResultante,
        String nota) {

    public Movimiento {
        Objects.requireNonNull(fecha, "fecha");
        Objects.requireNonNull(codigoProducto, "codigoProducto");
        Objects.requireNonNull(tipo, "tipo");
        nota = nota == null ? "" : nota.strip();
    }
}
