package inventario.servicio;

import inventario.modelo.Movimiento;
import inventario.modelo.Producto;
import inventario.persistencia.MovimientoRepositorio;

import java.util.ArrayList;
import java.util.List;

/** Repositorio sin archivos, para probar el servicio de forma aislada. */
class MovimientoRepositorioEnMemoria implements MovimientoRepositorio {

    private final List<Movimiento> movimientos = new ArrayList<>();

    @Override
    public void registrar(Movimiento movimiento) {
        movimientos.add(movimiento);
    }

    @Override
    public List<Movimiento> listar() {
        return List.copyOf(movimientos);
    }

    @Override
    public List<Movimiento> listarPorProducto(String codigoProducto) {
        String codigo = Producto.normalizarCodigo(codigoProducto);
        return movimientos.stream().filter(m -> m.codigoProducto().equals(codigo)).toList();
    }
}
