package inventario.persistencia;

import inventario.modelo.Movimiento;
import inventario.modelo.TipoMovimiento;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface MovimientoRepositorio {

    void registrar(Movimiento movimiento);

    List<Movimiento> listar();

    List<Movimiento> listarPorProducto(String codigoProducto);

    /**
     * Movimientos entre dos fechas (ambas incluidas), en orden de registro. Esta versión filtra en memoria; la
     * de SQLite lo resuelve con una consulta que usa el índice por fecha.
     */
    default List<Movimiento> listarEntre(LocalDate desde, LocalDate hasta) {
        return listar().stream()
                .filter(m -> {
                    LocalDate dia = m.fecha().toLocalDate();
                    return !dia.isBefore(desde) && !dia.isAfter(hasta);
                })
                .toList();
    }

    /** Fecha de la última venta de cada producto que se vendió alguna vez (código → fecha). */
    default Map<String, LocalDate> ultimaVentaPorProducto() {
        Map<String, LocalDate> ultima = new HashMap<>();
        for (Movimiento m : listar()) {
            if (m.tipo() == TipoMovimiento.SALIDA) {
                ultima.merge(m.codigoProducto(), m.fecha().toLocalDate(), (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        return ultima;
    }
}
