package inventario.servicio;

import inventario.modelo.Producto;
import inventario.persistencia.ProductoRepositorio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Repositorio sin archivos, para probar el servicio de forma aislada. */
class ProductoRepositorioEnMemoria implements ProductoRepositorio {

    private final Map<String, Producto> productos = new LinkedHashMap<>();

    /** Simula un disco que no se puede escribir. */
    boolean fallarAlGuardar;

    @Override
    public List<Producto> listar() {
        return List.copyOf(productos.values());
    }

    @Override
    public Optional<Producto> buscarPorCodigo(String codigo) {
        return Optional.ofNullable(productos.get(Producto.normalizarCodigo(codigo)));
    }

    @Override
    public void guardar(Producto producto) {
        if (fallarAlGuardar) {
            throw new UncheckedIOException(new IOException("Disco lleno"));
        }
        productos.put(producto.getCodigo(), producto);
    }

    @Override
    public boolean eliminar(String codigo) {
        return productos.remove(Producto.normalizarCodigo(codigo)) != null;
    }
}
