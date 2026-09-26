package inventario.servicio;

import inventario.modelo.Movimiento;
import inventario.modelo.Producto;
import inventario.modelo.TipoMovimiento;
import inventario.persistencia.MovimientoRepositorio;
import inventario.persistencia.ProductoRepositorio;
import inventario.persistencia.Transacciones;

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
    private final Transacciones transacciones;
    private final Clock reloj;

    public InventarioServicio(ProductoRepositorio productos, MovimientoRepositorio movimientos,
                              Transacciones transacciones) {
        this(productos, movimientos, transacciones, Clock.systemDefaultZone());
    }

    public InventarioServicio(ProductoRepositorio productos, MovimientoRepositorio movimientos, Clock reloj) {
        this(productos, movimientos, Transacciones.NINGUNA, reloj);
    }

    public InventarioServicio(ProductoRepositorio productos, MovimientoRepositorio movimientos,
                              Transacciones transacciones, Clock reloj) {
        this.productos = productos;
        this.movimientos = movimientos;
        this.transacciones = transacciones;
        this.reloj = reloj;
    }

    // ---- Catálogo ----

    public Producto registrarProducto(String codigo, String nombre, String categoria,
                                      BigDecimal precio, int stockInicial, int stockMinimo) {
        Producto producto = crearValidado(codigo, nombre, categoria, precio, stockInicial, stockMinimo);
        validarCodigoDisponible(producto.getCodigo());
        try {
            transacciones.ejecutar(() -> {
                productos.guardar(producto);
                if (stockInicial > 0) {
                    registrarMovimiento(producto, TipoMovimiento.ENTRADA, stockInicial, "Stock inicial");
                }
            });
        } catch (RuntimeException e) {
            // Con base de datos la transacción ya se revirtió; con archivos hay que deshacer a mano.
            deshacer(e, () -> productos.eliminar(producto.getCodigo()));
            throw new InventarioException("No se pudo registrar el producto; no se guardó ningún cambio.", e);
        }
        return producto;
    }

    public Producto actualizarProducto(String codigo, String nombre, String categoria,
                                       BigDecimal precio, int stockMinimo) {
        Producto actual = obtenerActivo(codigo);
        // Se guarda una copia: si algo falla, el producto original queda intacto.
        Producto actualizado = crearValidado(actual.getCodigo(), nombre, categoria, precio,
                actual.getStock(), stockMinimo);
        guardarProducto(actualizado);
        return actualizado;
    }

    /** Baja lógica: el producto sale del catálogo pero conserva su historial y puede reactivarse. */
    public void darDeBajaProducto(String codigo) {
        cambiarEstado(obtenerActivo(codigo), false);
    }

    public void reactivarProducto(String codigo) {
        Producto producto = obtener(codigo);
        if (producto.isActivo()) {
            throw new InventarioException("El producto " + producto.getCodigo() + " ya está activo.");
        }
        cambiarEstado(producto, true);
    }

    /** Falla si el código está vacío o ya lo usa otro producto, activo o dado de baja. */
    public void validarCodigoDisponible(String codigo) {
        productos.buscarPorCodigo(normalizar(codigo)).ifPresent(p -> {
            throw new InventarioException(p.isActivo()
                    ? "Ya existe un producto con el código " + p.getCodigo() + "."
                    : "El código " + p.getCodigo() + " pertenece a un producto dado de baja; reactívelo en su lugar.");
        });
    }

    /** Devuelve el producto aunque esté dado de baja (p. ej. para consultar su historial). */
    public Producto obtener(String codigo) {
        try {
            return productos.buscarPorCodigo(codigo)
                    .orElseThrow(() -> new InventarioException("No existe un producto con el código " + codigo + "."));
        } catch (IllegalArgumentException e) {
            throw new InventarioException(e.getMessage());
        }
    }

    /** Devuelve el producto solo si está activo; los dados de baja no se pueden editar ni mover. */
    public Producto obtenerActivo(String codigo) {
        Producto producto = obtener(codigo);
        if (!producto.isActivo()) {
            throw new InventarioException("El producto " + producto.getCodigo() + " está dado de baja.");
        }
        return producto;
    }

    /** Catálogo de productos activos, ordenado por código. */
    public List<Producto> listarProductos() {
        return ordenadosPorCodigo(true);
    }

    public List<Producto> listarProductosDadosDeBaja() {
        return ordenadosPorCodigo(false);
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
        Producto producto = obtenerActivo(codigo);
        int nuevoStock;
        try {
            nuevoStock = Math.addExact(producto.getStock(), cantidad);
        } catch (ArithmeticException e) {
            throw new InventarioException("La cantidad es demasiado grande.");
        }
        return cambiarStock(producto, nuevoStock, TipoMovimiento.ENTRADA, cantidad, nota);
    }

    public Producto registrarSalida(String codigo, int cantidad, String nota) {
        validarCantidad(cantidad);
        Producto producto = obtenerActivo(codigo);
        if (cantidad > producto.getStock()) {
            throw new InventarioException("Stock insuficiente: hay " + producto.getStock()
                    + " unidades de " + producto.getNombre() + ".");
        }
        return cambiarStock(producto, producto.getStock() - cantidad, TipoMovimiento.SALIDA, cantidad, nota);
    }

    /** Fija el stock a un valor contado físicamente; la cantidad registrada es la diferencia. */
    public Producto ajustarStock(String codigo, int nuevoStock, String nota) {
        if (nuevoStock < 0) {
            throw new InventarioException("El stock no puede ser negativo.");
        }
        Producto producto = obtenerActivo(codigo);
        int diferencia = nuevoStock - producto.getStock();
        if (diferencia == 0) {
            return producto;
        }
        return cambiarStock(producto, nuevoStock, TipoMovimiento.AJUSTE, diferencia, nota);
    }

    // ---- Consultas ----

    public List<Producto> productosConStockBajo() {
        return listarProductos().stream()
                .filter(Producto::tieneStockBajo)
                .sorted(Comparator.comparingInt(Producto::getStock))
                .toList();
    }

    public BigDecimal valorTotalInventario() {
        return listarProductos().stream()
                .map(Producto::valorEnStock)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int unidadesTotales() {
        return listarProductos().stream().mapToInt(Producto::getStock).sum();
    }

    public List<Movimiento> historial(String codigo) {
        return movimientos.listarPorProducto(obtener(codigo).getCodigo());
    }

    public List<Movimiento> ultimosMovimientos(int limite) {
        List<Movimiento> todos = movimientos.listar();
        return todos.subList(Math.max(0, todos.size() - limite), todos.size()).reversed();
    }

    // ---- Auxiliares ----

    private List<Producto> ordenadosPorCodigo(boolean activos) {
        return productos.listar().stream()
                .filter(p -> p.isActivo() == activos)
                .sorted(Comparator.comparing(Producto::getCodigo))
                .toList();
    }

    private void cambiarEstado(Producto producto, boolean activo) {
        producto.setActivo(activo);
        try {
            productos.guardar(producto);
        } catch (RuntimeException e) {
            producto.setActivo(!activo);
            throw new InventarioException("No se pudo guardar el producto: " + e.getMessage(), e);
        }
    }

    private static String normalizar(String codigo) {
        try {
            return Producto.normalizarCodigo(codigo);
        } catch (IllegalArgumentException e) {
            throw new InventarioException(e.getMessage());
        }
    }

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

    /**
     * Aplica el nuevo stock y registra el movimiento como una sola operación:
     * si cualquiera de los dos guardados falla, el stock vuelve a su valor anterior.
     */
    private Producto cambiarStock(Producto producto, int nuevoStock, TipoMovimiento tipo, int cantidad, String nota) {
        int stockAnterior = producto.getStock();
        producto.setStock(nuevoStock);
        try {
            transacciones.ejecutar(() -> {
                productos.guardar(producto);
                registrarMovimiento(producto, tipo, cantidad, nota);
            });
        } catch (RuntimeException e) {
            producto.setStock(stockAnterior);
            deshacer(e, () -> productos.guardar(producto));
            throw new InventarioException("No se pudo guardar el movimiento; el stock no se modificó.", e);
        }
        return producto;
    }

    private void guardarProducto(Producto producto) {
        try {
            productos.guardar(producto);
        } catch (RuntimeException e) {
            throw new InventarioException("No se pudo guardar el producto: " + e.getMessage(), e);
        }
    }

    /** Ejecuta una acción de reversión; si también falla, se adjunta al error original. */
    private static void deshacer(RuntimeException original, Runnable reversion) {
        try {
            reversion.run();
        } catch (RuntimeException e) {
            original.addSuppressed(e);
        }
    }

    private void registrarMovimiento(Producto producto, TipoMovimiento tipo, int cantidad, String nota) {
        LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);
        movimientos.registrar(new Movimiento(ahora, producto.getCodigo(), tipo, cantidad, producto.getStock(), nota));
    }
}
