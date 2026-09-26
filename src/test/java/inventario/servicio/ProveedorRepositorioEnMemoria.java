package inventario.servicio;

import inventario.modelo.Proveedor;
import inventario.persistencia.ProveedorRepositorio;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Repositorio sin archivos, para probar los servicios de forma aislada. */
class ProveedorRepositorioEnMemoria implements ProveedorRepositorio {

    private final Map<Long, Proveedor> proveedores = new LinkedHashMap<>();
    private long siguienteId = 1;

    @Override
    public List<Proveedor> listar() {
        return proveedores.values().stream().sorted(Comparator.comparing(Proveedor::nombre)).toList();
    }

    @Override
    public Optional<Proveedor> buscarPorId(long id) {
        return Optional.ofNullable(proveedores.get(id));
    }

    @Override
    public Proveedor guardar(Proveedor proveedor) {
        Proveedor conId = proveedor.id() == null ? proveedor.conId(siguienteId++) : proveedor;
        proveedores.put(conId.id(), conId);
        return conId;
    }
}
