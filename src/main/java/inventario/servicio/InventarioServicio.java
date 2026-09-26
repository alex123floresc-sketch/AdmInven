package inventario.servicio;

import inventario.modelo.Movimiento;
import inventario.modelo.Permiso;
import inventario.modelo.Producto;
import inventario.modelo.TipoMovimiento;
import inventario.persistencia.MovimientoRepositorio;
import inventario.persistencia.ProductoRepositorio;
import inventario.persistencia.Transacciones;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class InventarioServicio {

    private final ProductoRepositorio productos;
    private final MovimientoRepositorio movimientos;
    private final Transacciones transacciones;
    private final Clock reloj;
    private final Sesion sesion;

    public InventarioServicio(ProductoRepositorio productos, MovimientoRepositorio movimientos,
                              Transacciones transacciones) {
        this(productos, movimientos, transacciones, Clock.systemDefaultZone());
    }

    public InventarioServicio(ProductoRepositorio productos, MovimientoRepositorio movimientos, Clock reloj) {
        this(productos, movimientos, Transacciones.NINGUNA, reloj);
    }

    public InventarioServicio(ProductoRepositorio productos, MovimientoRepositorio movimientos,
                              Transacciones transacciones, Clock reloj) {
        this(productos, movimientos, transacciones, reloj, Sesion.sinRestricciones());
    }

    public InventarioServicio(ProductoRepositorio productos, MovimientoRepositorio movimientos,
                              Transacciones transacciones, Clock reloj, Sesion sesion) {
        this.productos = productos;
        this.movimientos = movimientos;
        this.transacciones = transacciones;
        this.reloj = reloj;
        this.sesion = sesion;
    }

    // ---- Catálogo ----

    /** Registra un producto sin costo conocido (costo 0). */
    public Producto registrarProducto(String codigo, String nombre, String categoria,
                                      BigDecimal precio, int stockInicial, int stockMinimo) {
        return registrarProducto(codigo, nombre, categoria, precio, BigDecimal.ZERO, stockInicial, stockMinimo);
    }

    public Producto registrarProducto(String codigo, String nombre, String categoria, BigDecimal precio,
                                      BigDecimal costo, int stockInicial, int stockMinimo) {
        sesion.requerir(Permiso.GESTIONAR_PRODUCTOS);
        Producto producto = crearValidado(codigo, nombre, categoria, precio, costo, stockInicial, stockMinimo);
        validarCodigoDisponible(producto.getCodigo());
        try {
            transacciones.ejecutar(() -> {
                productos.guardar(producto);
                if (stockInicial > 0) {
                    registrarMovimiento(producto, new Cambio(TipoMovimiento.ENTRADA, stockInicial,
                            BigDecimal.ZERO, producto.getCosto(), null, "Stock inicial"));
                }
            });
        } catch (RuntimeException e) {
            // Con base de datos la transacción ya se revirtió; con archivos hay que deshacer a mano.
            deshacer(e, () -> productos.eliminar(producto.getCodigo()));
            throw new InventarioException("No se pudo registrar el producto; no se guardó ningún cambio.", e);
        }
        return producto;
    }

    /** Actualiza los datos del producto conservando su costo. */
    public Producto actualizarProducto(String codigo, String nombre, String categoria,
                                       BigDecimal precio, int stockMinimo) {
        return actualizarProducto(codigo, nombre, categoria, precio, obtenerActivo(codigo).getCosto(), stockMinimo);
    }

    public Producto actualizarProducto(String codigo, String nombre, String categoria, BigDecimal precio,
                                       BigDecimal costo, int stockMinimo) {
        sesion.requerir(Permiso.GESTIONAR_PRODUCTOS);
        Producto actual = obtenerActivo(codigo);
        // Se guarda una copia: si algo falla, el producto original queda intacto.
        Producto actualizado = crearValidado(actual.getCodigo(), nombre, categoria, precio, costo,
                actual.getStock(), stockMinimo);
        guardarProducto(actualizado);
        return actualizado;
    }

    public record ResultadoImportacion(int importados, List<String> omitidos) {
    }

    /**
     * Registra cada producto como si se diera de alta a mano (su stock entra como "Stock inicial").
     * Los que no se pueden registrar (código repetido, datos inválidos) se omiten y se informan.
     */
    public ResultadoImportacion importarProductos(List<Producto> nuevos) {
        sesion.requerir(Permiso.GESTIONAR_PRODUCTOS);
        int importados = 0;
        List<String> omitidos = new ArrayList<>();
        for (Producto p : nuevos) {
            try {
                registrarProducto(p.getCodigo(), p.getNombre(), p.getCategoria(), p.getPrecio(), p.getCosto(),
                        p.getStock(), p.getStockMinimo());
                importados++;
            } catch (InventarioException e) {
                omitidos.add(p.getCodigo() + ": " + e.getMessage());
            }
        }
        return new ResultadoImportacion(importados, List.copyOf(omitidos));
    }

    /** Baja lógica: el producto sale del catálogo pero conserva su historial y puede reactivarse. */
    public void darDeBajaProducto(String codigo) {
        sesion.requerir(Permiso.GESTIONAR_PRODUCTOS);
        cambiarEstado(obtenerActivo(codigo), false);
    }

    public void reactivarProducto(String codigo) {
        sesion.requerir(Permiso.GESTIONAR_PRODUCTOS);
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

    /** Entrada al costo actual del producto y sin proveedor. */
    public Producto registrarEntrada(String codigo, int cantidad, String nota) {
        return registrarEntrada(codigo, cantidad, null, null, nota);
    }

    /**
     * Registra una compra. El costo del producto pasa a ser el promedio ponderado entre el stock que había y
     * lo que ingresa.
     *
     * @param costoUnitario costo de compra por unidad; {@code null} usa el costo actual del producto
     * @param proveedorId   proveedor de la compra, o {@code null}
     */
    public Producto registrarEntrada(String codigo, int cantidad, BigDecimal costoUnitario, Long proveedorId,
                                     String nota) {
        sesion.requerir(Permiso.REGISTRAR_COMPRAS);
        validarCantidad(cantidad);
        Producto producto = obtenerActivo(codigo);
        BigDecimal costoCompra = costoUnitario == null ? producto.getCosto() : costoUnitario;
        if (costoCompra.signum() < 0) {
            throw new InventarioException("El costo de compra no puede ser negativo.");
        }
        int nuevoStock;
        try {
            nuevoStock = Math.addExact(producto.getStock(), cantidad);
        } catch (ArithmeticException e) {
            throw new InventarioException("La cantidad es demasiado grande.");
        }
        BigDecimal nuevoCosto = producto.getCosto().multiply(BigDecimal.valueOf(producto.getStock()))
                .add(costoCompra.multiply(BigDecimal.valueOf(cantidad)))
                .divide(BigDecimal.valueOf(nuevoStock), 2, RoundingMode.HALF_UP);
        return cambiarStock(producto, nuevoStock, nuevoCosto,
                new Cambio(TipoMovimiento.ENTRADA, cantidad, BigDecimal.ZERO, costoCompra, proveedorId, nota));
    }

    /** Venta al precio actual del producto; guarda precio y costo para calcular la ganancia. */
    public Producto registrarSalida(String codigo, int cantidad, String nota) {
        sesion.requerir(Permiso.REGISTRAR_VENTAS);
        validarCantidad(cantidad);
        Producto producto = obtenerActivo(codigo);
        if (cantidad > producto.getStock()) {
            throw new InventarioException("Stock insuficiente: hay " + producto.getStock()
                    + " unidades de " + producto.getNombre() + ".");
        }
        return cambiarStock(producto, producto.getStock() - cantidad, producto.getCosto(),
                new Cambio(TipoMovimiento.SALIDA, cantidad, producto.getPrecio(), producto.getCosto(), null, nota));
    }

    /** Fija el stock a un valor contado físicamente; la cantidad registrada es la diferencia. */
    public Producto ajustarStock(String codigo, int nuevoStock, String nota) {
        sesion.requerir(Permiso.AJUSTAR_STOCK);
        if (nuevoStock < 0) {
            throw new InventarioException("El stock no puede ser negativo.");
        }
        Producto producto = obtenerActivo(codigo);
        int diferencia = nuevoStock - producto.getStock();
        if (diferencia == 0) {
            return producto;
        }
        return cambiarStock(producto, nuevoStock, producto.getCosto(),
                new Cambio(TipoMovimiento.AJUSTE, diferencia, BigDecimal.ZERO, producto.getCosto(), null, nota));
    }

    // ---- Consultas ----

    public List<Producto> productosConStockBajo() {
        return listarProductos().stream()
                .filter(Producto::tieneStockBajo)
                .sorted(Comparator.comparingInt(Producto::getStock))
                .toList();
    }

    /** Valor del stock activo a precio de venta. */
    public BigDecimal valorTotalInventario() {
        return listarProductos().stream()
                .map(Producto::valorEnStock)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Valor del stock activo a costo de compra: el dinero invertido en mercadería. */
    public BigDecimal valorInventarioAlCosto() {
        return listarProductos().stream()
                .map(Producto::costoEnStock)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int unidadesTotales() {
        return listarProductos().stream().mapToInt(Producto::getStock).sum();
    }

    public List<Movimiento> historial(String codigo) {
        return movimientos.listarPorProducto(obtener(codigo).getCodigo());
    }

    /** Todos los movimientos, del más reciente al más antiguo. */
    public List<Movimiento> listarMovimientos() {
        return movimientos.listar().reversed();
    }

    /** Categorías usadas por los productos activos, en orden alfabético. */
    public List<String> listarCategorias() {
        return listarProductos().stream().map(Producto::getCategoria).distinct().sorted().toList();
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

    private Producto crearValidado(String codigo, String nombre, String categoria, BigDecimal precio,
                                   BigDecimal costo, int stock, int stockMinimo) {
        try {
            return new Producto(codigo, nombre, categoria, precio, costo, stock, stockMinimo);
        } catch (IllegalArgumentException e) {
            throw new InventarioException(e.getMessage());
        }
    }

    private void validarCantidad(int cantidad) {
        if (cantidad <= 0) {
            throw new InventarioException("La cantidad debe ser mayor que cero.");
        }
    }

    /** Datos del movimiento que acompaña a un cambio de stock. */
    private record Cambio(TipoMovimiento tipo, int cantidad, BigDecimal precioUnitario, BigDecimal costoUnitario,
                          Long proveedorId, String nota) {
    }

    /**
     * Aplica el nuevo stock (y costo) y registra el movimiento como una sola operación:
     * si cualquiera de los dos guardados falla, el producto vuelve a su estado anterior.
     */
    private Producto cambiarStock(Producto producto, int nuevoStock, BigDecimal nuevoCosto, Cambio cambio) {
        int stockAnterior = producto.getStock();
        BigDecimal costoAnterior = producto.getCosto();
        producto.setStock(nuevoStock);
        producto.setCosto(nuevoCosto);
        try {
            transacciones.ejecutar(() -> {
                productos.guardar(producto);
                registrarMovimiento(producto, cambio);
            });
        } catch (RuntimeException e) {
            producto.setStock(stockAnterior);
            producto.setCosto(costoAnterior);
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

    private void registrarMovimiento(Producto producto, Cambio c) {
        LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);
        movimientos.registrar(new Movimiento(ahora, producto.getCodigo(), c.tipo(), c.cantidad(),
                producto.getStock(), c.nota(), c.precioUnitario(), c.costoUnitario(), c.proveedorId(),
                sesion.nombreUsuario()));
    }
}
