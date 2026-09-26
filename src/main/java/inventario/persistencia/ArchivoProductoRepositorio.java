package inventario.persistencia;

import inventario.modelo.Producto;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ArchivoProductoRepositorio implements ProductoRepositorio {

    private static final String CABECERA = "codigo;nombre;categoria;precio;stock;stockMinimo";

    private final Path archivo;
    private final Map<String, Producto> productos = new LinkedHashMap<>();

    public ArchivoProductoRepositorio(Path archivo) {
        this.archivo = archivo;
        cargar();
    }

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
        Producto anterior = productos.put(producto.getCodigo(), producto);
        try {
            persistir();
        } catch (RuntimeException e) {
            restaurar(producto.getCodigo(), anterior);
            throw e;
        }
    }

    @Override
    public boolean eliminar(String codigo) {
        String clave = Producto.normalizarCodigo(codigo);
        Producto eliminado = productos.remove(clave);
        if (eliminado == null) {
            return false;
        }
        try {
            persistir();
        } catch (RuntimeException e) {
            // Se pierde la posición original en el orden de inserción; no afecta a los listados, que se ordenan.
            productos.put(clave, eliminado);
            throw e;
        }
        return true;
    }

    /** Deja la memoria como estaba si el archivo no pudo actualizarse. */
    private void restaurar(String codigo, Producto anterior) {
        if (anterior == null) {
            productos.remove(codigo);
        } else {
            productos.put(codigo, anterior);
        }
    }

    private void cargar() {
        List<String> lineas = Archivos.leerLineas(archivo);
        for (int i = 1; i < lineas.size(); i++) {
            String linea = lineas.get(i);
            if (linea.isBlank()) {
                continue;
            }
            List<String> c = Csv.separar(linea);
            if (c.size() < 6) {
                throw new IllegalStateException("Línea " + (i + 1) + " inválida en " + archivo);
            }
            Producto p = new Producto(c.get(0), c.get(1), c.get(2),
                    new BigDecimal(c.get(3)), Integer.parseInt(c.get(4)), Integer.parseInt(c.get(5)));
            productos.put(p.getCodigo(), p);
        }
    }

    private void persistir() {
        List<String> lineas = new ArrayList<>(productos.size() + 1);
        lineas.add(CABECERA);
        for (Producto p : productos.values()) {
            lineas.add(Csv.unir(p.getCodigo(), p.getNombre(), p.getCategoria(),
                    p.getPrecio().toPlainString(), p.getStock(), p.getStockMinimo()));
        }
        Archivos.escribirLineas(archivo, lineas);
    }
}
