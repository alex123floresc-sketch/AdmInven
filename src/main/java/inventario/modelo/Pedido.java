package inventario.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Pedido que un cliente hace desde la tienda en línea. Guarda el nombre y el precio de cada producto tal como
 * los vio el cliente; el stock recién se descuenta cuando el personal lo atiende.
 *
 * @param id            asignado por el almacenamiento; {@code null} si aún no se guardó
 * @param atendidoPor   usuario que lo atendió o canceló; vacío mientras está pendiente
 * @param fechaAtencion cuándo se atendió o canceló; {@code null} mientras está pendiente
 */
public record Pedido(Long id, LocalDateTime fecha, String cliente, String telefono, String nota,
                     List<LineaPedido> lineas, EstadoPedido estado, String atendidoPor,
                     LocalDateTime fechaAtencion) {

    public static final int MAXIMO_LINEAS = 30;
    public static final int MAXIMO_UNIDADES_POR_LINEA = 999;

    public record LineaPedido(String codigo, String nombre, int cantidad, BigDecimal precio) {

        public LineaPedido {
            codigo = Producto.normalizarCodigo(codigo);
            Objects.requireNonNull(nombre);
            if (cantidad <= 0 || cantidad > MAXIMO_UNIDADES_POR_LINEA) {
                throw new IllegalArgumentException("La cantidad de cada producto debe estar entre 1 y "
                        + MAXIMO_UNIDADES_POR_LINEA + ".");
            }
            Objects.requireNonNull(precio);
        }

        public BigDecimal subtotal() {
            return precio.multiply(BigDecimal.valueOf(cantidad));
        }
    }

    public Pedido {
        Objects.requireNonNull(fecha);
        cliente = limpiar(cliente);
        telefono = limpiar(telefono);
        nota = limpiar(nota);
        lineas = List.copyOf(lineas);
        Objects.requireNonNull(estado);
        atendidoPor = limpiar(atendidoPor);
        if (cliente.isEmpty()) {
            throw new IllegalArgumentException("Escriba su nombre.");
        }
        if (cliente.length() > 80) {
            throw new IllegalArgumentException("El nombre es demasiado largo (máximo 80 caracteres).");
        }
        if (!telefono.matches("\\+?[\\d ]{6,20}") || telefono.replace(" ", "").length() > 15) {
            throw new IllegalArgumentException("Escriba un teléfono válido, por ejemplo 987 654 321.");
        }
        if (nota.length() > 300) {
            throw new IllegalArgumentException("La nota es demasiado larga (máximo 300 caracteres).");
        }
        if (lineas.isEmpty()) {
            throw new IllegalArgumentException("El pedido está vacío.");
        }
        if (lineas.size() > MAXIMO_LINEAS) {
            throw new IllegalArgumentException("Un pedido puede tener hasta " + MAXIMO_LINEAS + " productos.");
        }
    }

    public BigDecimal total() {
        return lineas.stream().map(LineaPedido::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Pedido conId(long nuevoId) {
        return new Pedido(nuevoId, fecha, cliente, telefono, nota, lineas, estado, atendidoPor, fechaAtencion);
    }

    /** El pedido pasa a atendido o cancelado por {@code usuario} en {@code cuando}. */
    public Pedido cerrado(EstadoPedido nuevoEstado, String usuario, LocalDateTime cuando) {
        return new Pedido(id, fecha, cliente, telefono, nota, lineas, nuevoEstado, usuario, cuando);
    }

    private static String limpiar(String texto) {
        return Objects.requireNonNullElse(texto, "").strip();
    }
}
