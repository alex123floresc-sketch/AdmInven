package inventario.servicio;

import inventario.modelo.EstadoPedido;
import inventario.modelo.Pedido;
import inventario.persistencia.Transacciones;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PedidoServicioTest {

    private InventarioServicio inventario;
    private PedidoRepositorioEnMemoria repo;
    /** Lo que usa un visitante de la tienda, sin iniciar sesión. */
    private PedidoServicio delCliente;
    /** Lo que usa el personal (aquí, sin restricciones). */
    private PedidoServicio delPersonal;

    @BeforeEach
    void preparar() {
        ProductoRepositorioEnMemoria productos = new ProductoRepositorioEnMemoria();
        MovimientoRepositorioEnMemoria movimientos = new MovimientoRepositorioEnMemoria();
        Clock reloj = Clock.systemDefaultZone();
        inventario = new InventarioServicio(productos, movimientos, reloj);
        inventario.registrarProducto("ARR", "Arroz 5 kg", "Abarrotes", new BigDecimal("21.90"), 10, 2);
        inventario.registrarProducto("AGU", "Agua 625 ml", "Bebidas", new BigDecimal("1.50"), 3, 1);
        inventario.registrarProducto("YOG", "Yogur 1 L", "Lácteos", new BigDecimal("7.90"), 0, 1);
        repo = new PedidoRepositorioEnMemoria();
        Sesion anonima = Sesion.nueva();
        delCliente = new PedidoServicio(repo, new InventarioServicio(productos, movimientos, Transacciones.NINGUNA,
                reloj, anonima), Transacciones.NINGUNA, reloj, anonima);
        delPersonal = new PedidoServicio(repo, inventario, Transacciones.NINGUNA, reloj, Sesion.sinRestricciones());
    }

    private static List<InventarioServicio.ItemVenta> items(Object... codigoYCantidad) {
        List<InventarioServicio.ItemVenta> lista = new ArrayList<>();
        for (int i = 0; i < codigoYCantidad.length; i += 2) {
            lista.add(new InventarioServicio.ItemVenta((String) codigoYCantidad[i], (Integer) codigoYCantidad[i + 1]));
        }
        return lista;
    }

    @Test
    void unVisitanteHacePedidosSinTocarElStock() {
        Pedido p = delCliente.registrar(" Rosa Mamani ", "987 654 321", "Paso a las 6",
                items("arr", 2, "AGU", 1, "ARR", 1));

        assertEquals(1L, p.id());
        assertEquals("Rosa Mamani", p.cliente());
        assertEquals(EstadoPedido.PENDIENTE, p.estado());
        assertEquals(2, p.lineas().size(), "las líneas del mismo producto se suman");
        assertEquals(3, p.lineas().getFirst().cantidad());
        assertEquals(new BigDecimal("67.20"), p.total());
        assertEquals(10, inventario.obtener("ARR").getStock());
    }

    @Test
    void elPedidoSeValida() {
        assertEquals("Escriba su nombre.", assertThrows(InventarioException.class,
                () -> delCliente.registrar(" ", "987654321", "", items("ARR", 1))).getMessage());
        assertThrows(InventarioException.class, () -> delCliente.registrar("Rosa", "abc", "", items("ARR", 1)));
        assertThrows(InventarioException.class, () -> delCliente.registrar("Rosa", "987654321", "", List.of()));
        assertThrows(InventarioException.class, () -> delCliente.registrar("Rosa", "987654321", "", items("NO", 1)));
        assertThrows(InventarioException.class, () -> delCliente.registrar("Rosa", "987654321", "", items("ARR", 0)));
        assertEquals("Yogur 1 L está agotado.", assertThrows(InventarioException.class,
                () -> delCliente.registrar("Rosa", "987654321", "", items("YOG", 1))).getMessage());
        assertEquals("Solo quedan 3 unidades de Agua 625 ml.", assertThrows(InventarioException.class,
                () -> delCliente.registrar("Rosa", "987654321", "", items("AGU", 2, "AGU", 2))).getMessage());
        assertEquals(0, repo.listar().size());
    }

    @Test
    void soloElPersonalVeYAtiendeLosPedidos() {
        Pedido p = delCliente.registrar("Rosa", "987654321", "", items("ARR", 1));

        assertThrows(InventarioException.class, delCliente::listar);
        assertThrows(InventarioException.class, () -> delCliente.atender(p.id()));
        assertThrows(InventarioException.class, () -> delCliente.cancelar(p.id()));
        assertEquals(1, delPersonal.pendientes());
    }

    @Test
    void atenderRegistraLaVentaYCierraElPedido() {
        Pedido p = delCliente.registrar("Rosa", "987654321", "", items("ARR", 2, "AGU", 3));

        Pedido atendido = delPersonal.atender(p.id());

        assertEquals(EstadoPedido.ATENDIDO, atendido.estado());
        assertEquals("sistema", atendido.atendidoPor());
        assertEquals(8, inventario.obtener("ARR").getStock());
        assertEquals(0, inventario.obtener("AGU").getStock());
        assertEquals("Pedido #1 · Rosa", inventario.historial("AGU").getLast().nota());
        assertEquals(0, delPersonal.pendientes());
        assertEquals("El pedido #1 ya fue atendido.", assertThrows(InventarioException.class,
                () -> delPersonal.cancelar(p.id())).getMessage());
    }

    @Test
    void siYaNoHayStockElPedidoSiguePendiente() {
        Pedido p = delCliente.registrar("Rosa", "987654321", "", items("AGU", 3));
        inventario.registrarSalida("AGU", 2, "mostrador");

        assertThrows(InventarioException.class, () -> delPersonal.atender(p.id()));
        assertEquals(EstadoPedido.PENDIENTE, repo.buscarPorId(p.id()).orElseThrow().estado());

        assertEquals(EstadoPedido.CANCELADO, delPersonal.cancelar(p.id()).estado());
        assertEquals(1, inventario.obtener("AGU").getStock());
    }
}
