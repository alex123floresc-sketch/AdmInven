package inventario.persistencia;

import inventario.modelo.Proveedor;

import java.util.List;
import java.util.Optional;

public interface ProveedorRepositorio {

    /** Todos los proveedores, activos o no, ordenados por nombre. */
    List<Proveedor> listar();

    Optional<Proveedor> buscarPorId(long id);

    /** Inserta (si no tiene id) o actualiza; devuelve el proveedor con su id. */
    Proveedor guardar(Proveedor proveedor);
}
