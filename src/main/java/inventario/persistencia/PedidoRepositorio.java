package inventario.persistencia;

import inventario.modelo.Pedido;

import java.util.List;
import java.util.Optional;

public interface PedidoRepositorio {

    /** Todos los pedidos con sus líneas, los más recientes primero. */
    List<Pedido> listar();

    Optional<Pedido> buscarPorId(long id);

    /**
     * Un pedido nuevo (sin id) se inserta con sus líneas; uno existente solo actualiza su estado, porque las
     * líneas no cambian después de hecho el pedido.
     */
    Pedido guardar(Pedido pedido);
}
