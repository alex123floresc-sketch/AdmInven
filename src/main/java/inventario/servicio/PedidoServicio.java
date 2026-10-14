package inventario.servicio;

import inventario.modelo.EstadoPedido;
import inventario.modelo.Pedido;
import inventario.modelo.Permiso;
import inventario.modelo.Producto;
import inventario.persistencia.PedidoRepositorio;
import inventario.persistencia.Transacciones;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Pedidos de la tienda en línea. Cualquier visitante puede hacer un pedido (no descuenta stock); quien puede
 * vender lo atiende, y entonces se registra como venta, o lo cancela.
 */
public class PedidoServicio {

    private final PedidoRepositorio pedidos;
    private final InventarioServicio inventario;
    private final Transacciones transacciones;
    private final Clock reloj;
    private final Sesion sesion;

    public PedidoServicio(PedidoRepositorio pedidos, InventarioServicio inventario, Transacciones transacciones,
                          Clock reloj, Sesion sesion) {
        this.pedidos = pedidos;
        this.inventario = inventario;
        this.transacciones = transacciones;
        this.reloj = reloj;
        this.sesion = sesion;
    }

    /**
     * Pedido de un cliente; no requiere sesión. Cada producto debe estar en el catálogo y con stock suficiente
     * en este momento, y su precio queda fijado en el pedido.
     */
    public Pedido registrar(String cliente, String telefono, String nota, List<InventarioServicio.ItemVenta> items) {
        if (items == null || items.isEmpty()) {
            throw new InventarioException("El pedido está vacío.");
        }
        Map<String, Integer> porProducto = new LinkedHashMap<>();
        for (InventarioServicio.ItemVenta item : items) {
            if (item.cantidad() <= 0) {
                throw new InventarioException("La cantidad debe ser mayor que cero.");
            }
            porProducto.merge(inventario.obtenerActivo(item.codigo()).getCodigo(), item.cantidad(),
                    (a, b) -> Math.min(a + b, Pedido.MAXIMO_UNIDADES_POR_LINEA + 1));
        }
        List<Pedido.LineaPedido> lineas = new ArrayList<>();
        porProducto.forEach((codigo, cantidad) -> {
            Producto p = inventario.obtenerActivo(codigo);
            if (p.getStock() == 0) {
                throw new InventarioException(p.getNombre() + " está agotado.");
            }
            if (cantidad > p.getStock()) {
                throw new InventarioException("Solo quedan " + p.getStock() + " unidades de " + p.getNombre() + ".");
            }
            lineas.add(crear(() -> new Pedido.LineaPedido(codigo, p.getNombre(), cantidad, p.getPrecio())));
        });
        Pedido nuevo = crear(() -> new Pedido(null, ahora(), cliente, telefono, nota, lineas,
                EstadoPedido.PENDIENTE, "", null));
        return transacciones.ejecutar(() -> pedidos.guardar(nuevo));
    }

    /** Todos los pedidos, los más recientes primero (incluyen los datos de contacto del cliente). */
    public List<Pedido> listar() {
        sesion.requerir(Permiso.REGISTRAR_VENTAS);
        return pedidos.listar();
    }

    public long pendientes() {
        return listar().stream().filter(p -> p.estado() == EstadoPedido.PENDIENTE).count();
    }

    /**
     * Registra la venta de todas las líneas (al precio actual de cada producto) y marca el pedido como atendido;
     * si falta stock de alguna, no se hace nada.
     */
    public Pedido atender(long id) {
        sesion.requerir(Permiso.REGISTRAR_VENTAS);
        return transacciones.ejecutar(() -> {
            Pedido pedido = pendiente(id);
            inventario.registrarVenta(pedido.lineas().stream()
                    .map(l -> new InventarioServicio.ItemVenta(l.codigo(), l.cantidad())).toList(),
                    "Pedido #" + id + " · " + pedido.cliente());
            return pedidos.guardar(pedido.cerrado(EstadoPedido.ATENDIDO, sesion.nombreUsuario(), ahora()));
        });
    }

    public Pedido cancelar(long id) {
        sesion.requerir(Permiso.REGISTRAR_VENTAS);
        Pedido pedido = pendiente(id);
        return pedidos.guardar(pedido.cerrado(EstadoPedido.CANCELADO, sesion.nombreUsuario(), ahora()));
    }

    private Pedido pendiente(long id) {
        Pedido pedido = pedidos.buscarPorId(id)
                .orElseThrow(() -> new InventarioException("No existe el pedido #" + id + "."));
        if (pedido.estado() != EstadoPedido.PENDIENTE) {
            throw new InventarioException("El pedido #" + id + " ya fue "
                    + pedido.estado().toString().toLowerCase(Locale.ROOT) + ".");
        }
        return pedido;
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);
    }

    /** Los errores de validación del modelo llegan al cliente como mensajes. */
    private static <T> T crear(Supplier<T> constructor) {
        try {
            return constructor.get();
        } catch (IllegalArgumentException e) {
            throw new InventarioException(e.getMessage());
        }
    }
}
