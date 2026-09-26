package inventario.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Registro inmutable de un cambio de stock sobre un producto.
 *
 * @param cantidad        unidades; en los ajustes lleva signo (positivo si sobró, negativo si faltó)
 * @param precioUnitario  en las salidas, el precio de venta aplicado; 0 en los demás tipos
 * @param costoUnitario   costo por unidad: el de compra en las entradas, el del producto en salidas y ajustes
 * @param proveedorId     proveedor de una entrada, o {@code null}
 */
public record Movimiento(
        LocalDateTime fecha,
        String codigoProducto,
        TipoMovimiento tipo,
        int cantidad,
        int stockResultante,
        String nota,
        BigDecimal precioUnitario,
        BigDecimal costoUnitario,
        Long proveedorId) {

    public Movimiento {
        Objects.requireNonNull(fecha, "fecha");
        Objects.requireNonNull(codigoProducto, "codigoProducto");
        Objects.requireNonNull(tipo, "tipo");
        nota = nota == null ? "" : nota.strip();
        precioUnitario = importe(precioUnitario);
        costoUnitario = importe(costoUnitario);
    }

    /** Movimiento sin información económica (datos anteriores a la Fase 5). */
    public Movimiento(LocalDateTime fecha, String codigoProducto, TipoMovimiento tipo, int cantidad,
                      int stockResultante, String nota) {
        this(fecha, codigoProducto, tipo, cantidad, stockResultante, nota, BigDecimal.ZERO, BigDecimal.ZERO, null);
    }

    /** Ingreso de una venta: cantidad × precio (0 si no es una salida). */
    public BigDecimal importeVenta() {
        return tipo == TipoMovimiento.SALIDA ? precioUnitario.multiply(BigDecimal.valueOf(cantidad)) : BigDecimal.ZERO;
    }

    /** Costo total de las unidades del movimiento (siempre positivo). */
    public BigDecimal importeCosto() {
        return costoUnitario.multiply(BigDecimal.valueOf(Math.abs(cantidad)));
    }

    private static BigDecimal importe(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_UP);
    }
}
