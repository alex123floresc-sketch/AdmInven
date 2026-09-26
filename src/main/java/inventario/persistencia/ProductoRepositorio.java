package inventario.persistencia;

import inventario.modelo.Producto;

import java.util.List;
import java.util.Optional;

public interface ProductoRepositorio {

    List<Producto> listar();

    Optional<Producto> buscarPorCodigo(String codigo);

    /** Inserta el producto o reemplaza el existente con el mismo código. */
    void guardar(Producto producto);

    boolean eliminar(String codigo);
}
