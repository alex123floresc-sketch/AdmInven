package inventario.servicio;

import inventario.modelo.Movimiento;
import inventario.modelo.Producto;
import inventario.modelo.TipoMovimiento;
import inventario.persistencia.MovimientoRepositorio;
import inventario.persistencia.ProductoRepositorio;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class InventarioServicio {

    private final ProductoRepositorio productos;
    private final MovimientoRepositorio movimientos;
    private final Clock reloj;

    public InventarioServicio(ProductoRepositorio productos, MovimientoRepositorio movimientos) {
        this(productos, movimientos, Clock.systemDefaultZone());
    }

    public InventarioServicio(ProductoRepositorio productos, MovimientoRepositorio movimientos, Clock reloj) {
        this.productos = productos;
        this.movimientos = movimientos;
        this.reloj = reloj;
    }

    // ---- Catálogo ----

    public Producto registrarProducto(String codigo, String nombre, String categoria,
                                      BigDecimal precio, int stockInicial, int stockMinimo) {
        Producto producto = crearValidado(codigo, nombre, categoria, precio, stockInicial, stockMinimo);
        if (productos.buscarPorCodigo(producto.getCodigo()).isPresent()) {
            throw new InventarioException("Ya existe un producto con el código " + producto.getCodigo() + ".");
        }
        productos.guardar(producto);
        if (stockInicial > 0) {
            registrarMovimiento(producto, TipoMovimiento.ENTRADA, stockInicial, "Stock inicial");
        }
        return producto;
    }

    public Producto actualizarProducto(String codigo, String nombre, String categoria,
                                       BigDecimal precio, int stockMinimo) {
        Producto producto = obtener(codigo);
        // Se valida sobre una copia para no dejar el producto a medio modificar si algo falla.
        crearValidado(producto.getCodigo(), nombre, categoria, precio, producto.getStock(), stockMinimo);
        producto.setNombre(nombre);
        producto.setCategoria(categoria);
        producto.setPrecio(precio);
        producto.setStockMinimo(stockMinimo);
        productos.guardar(producto);
        return producto;
    }

    public void eliminarProducto(String codigo) {
        Producto producto = obtener(codigo);
        productos.eliminar(producto.getCodigo());
    }

    public boolean existe(String codigo) {
        try {
            return productos.buscarPorCodigo(codigo).isPresent();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public Producto obtener(String codigo) {
        try {
            return productos.buscarPorCodigo(codigo)
                    .orElseThrow(() -> new InventarioException("No existe un producto con el código " + codigo + "."));
        } catch (IllegalArgumentException e) {
            throw new InventarioException(e.getMessage());
        }
    }

    public List<Producto> listarProductos() {
        return productos.listar().stream()
                .sorted(Comparator.comparing(Producto::getCodigo))
                .toList();
    }

    /** Busca por coincidencia parcial en código, nombre o categoría, sin distinguir mayúsculas. */
    public List<Producto> buscar(String texto) {
        String t = texto == null ? "" : texto.strip().toLowerCase(Locale.ROOT);
        return listarProductos().stream()
                .filter(p -> p.getCodigo().toLowerCase(Locale.ROOT).contains(t)
                        || p.getNombre().toLowerCase(Locale.ROOT).contains(t)
                        || p.getCategoria().toLowerCase(Locale.ROOT).contains(t))
                .toList();
    }

    // ---- Stock ----

    public Producto registrarEntrada(String codigo, int cantidad, String nota) {
        validarCantidad(cantidad);
        Producto producto = obtener(codigo);
        producto.setStock(Math.addExact(producto.getStock(), cantidad));
        productos.guardar(producto);
        registrarMovimiento(producto, TipoMovimiento.ENTRADA, cantidad, nota);
        return producto;
    }

    public Producto registrarSalida(String codigo, int cantidad, String nota) {
        validarCantidad(cantidad);
        Producto producto = obtener(codigo);
        if (cantidad > producto.getStock()) {
            throw new InventarioException("Stock insuficiente: hay " + producto.getStock()
                    + " unidades de " + producto.getNombre() + ".");
        }
        producto.setStock(producto.getStock() - cantidad);
        productos.guardar(producto);
        registrarMovimiento(producto, TipoMovimiento.SALIDA, cantidad, nota);
        return producto;
    }

    /** Fija el stock a un valor contado físicamente; la cantidad registrada es la diferencia. */
    public Producto ajustarStock(String codigo, int nuevoStock, String nota) {
        if (nuevoStock < 0) {
            throw new InventarioException("El stock no puede ser negativo.");
        }
        Producto producto = obtener(codigo);
        int diferencia = nuevoStock - producto.getStock();
        if (diferencia == 0) {
            return producto;
        }
        producto.setStock(nuevoStock);
        productos.guardar(producto);
        registrarMovimiento(producto, TipoMovimiento.AJUSTE, diferencia, nota);
        return producto;
    }

    // ---- Consultas ----

    public List<Producto> productosConStockBajo() {
        return listarProductos().stream()
                .filter(Producto::tieneStockBajo)
                .sorted(Comparator.comparingInt(Producto::getStock))
                .toList();
    }

    public BigDecimal valorTotalInventario() {
        return productos.listar().stream()
                .map(Producto::valorEnStock)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int unidadesTotales() {
        return productos.listar().stream().mapToInt(Producto::getStock).sum();
    }

    public List<Movimiento> historial(String codigo) {
        return movimientos.listarPorProducto(obtener(codigo).getCodigo());
    }

    public List<Movimiento> ultimosMovimientos(int limite) {
        List<Movimiento> todos = movimientos.listar();
        return todos.subList(Math.max(0, todos.size() - limite), todos.size()).reversed();
    }

    // ---- Auxiliares ----

    private Producto crearValidado(String codigo, String nombre, String categoria,
                                   BigDecimal precio, int stock, int stockMinimo) {
        try {
            return new Producto(codigo, nombre, categoria, precio, stock, stockMinimo);
        } catch (IllegalArgumentException e) {
            throw new InventarioException(e.getMessage());
        }
    }

    private void validarCantidad(int cantidad) {
        if (cantidad <= 0) {
            throw new InventarioException("La cantidad debe ser mayor que cero.");
        }
    }

    private void registrarMovimiento(Producto producto, TipoMovimiento tipo, int cantidad, String nota) {
        LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);
        movimientos.registrar(new Movimiento(ahora, producto.getCodigo(), tipo, cantidad, producto.getStock(), nota));
    }
}
