package inventario.persistencia;

import inventario.modelo.Movimiento;

import java.util.List;

public interface MovimientoRepositorio {

    void registrar(Movimiento movimiento);

    List<Movimiento> listar();

    List<Movimiento> listarPorProducto(String codigoProducto);
}
