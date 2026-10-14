package inventario.servicio;

import inventario.modelo.Pedido;
import inventario.persistencia.PedidoRepositorio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Repositorio sin archivos, para probar los servicios de forma aislada. */
class PedidoRepositorioEnMemoria implements PedidoRepositorio {

    private final Map<Long, Pedido> pedidos = new LinkedHashMap<>();
    private long siguienteId = 1;

    @Override
    public List<Pedido> listar() {
        return new ArrayList<>(pedidos.values()).reversed();
    }

    @Override
    public Optional<Pedido> buscarPorId(long id) {
        return Optional.ofNullable(pedidos.get(id));
    }

    @Override
    public Pedido guardar(Pedido pedido) {
        Pedido conId = pedido.id() == null ? pedido.conId(siguienteId++) : pedido;
        pedidos.put(conId.id(), conId);
        return conId;
    }
}
