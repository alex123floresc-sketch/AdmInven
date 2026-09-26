package inventario.servicio;

import inventario.modelo.Movimiento;
import inventario.modelo.Producto;
import inventario.modelo.Proveedor;
import inventario.modelo.TipoMovimiento;
import inventario.persistencia.ExportadorCsv;
import inventario.persistencia.MovimientoRepositorio;
import inventario.persistencia.ProductoRepositorio;
import inventario.persistencia.ProveedorRepositorio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reportes para decidir qué comprar y qué deja ganancia. Solo lee datos; nunca los modifica. */
public class ReporteServicio {

    /** Ventas de un producto en un período. */
    public record LineaVenta(String codigo, String nombre, int unidades, BigDecimal ingresos, BigDecimal costo) {

        public BigDecimal ganancia() {
            return ingresos.subtract(costo);
        }

        public BigDecimal margenPorcentaje() {
            return porcentaje(ganancia(), ingresos);
        }
    }

    /** Ventas de un período, por producto y en total. */
    public record ReporteVentas(LocalDate desde, LocalDate hasta, List<LineaVenta> lineas) {

        public int unidades() {
            return lineas.stream().mapToInt(LineaVenta::unidades).sum();
        }

        public BigDecimal ingresos() {
            return sumar(lineas, LineaVenta::ingresos);
        }

        public BigDecimal costo() {
            return sumar(lineas, LineaVenta::costo);
        }

        public BigDecimal ganancia() {
            return ingresos().subtract(costo());
        }

        public BigDecimal margenPorcentaje() {
            return porcentaje(ganancia(), ingresos());
        }
    }

    public record VentaDiaria(LocalDate fecha, BigDecimal ingresos, BigDecimal ganancia) {
    }

    /** Producto con stock que no se vende desde hace tiempo; {@code ultimaVenta} es null si nunca se vendió. */
    public record ProductoSinRotacion(Producto producto, LocalDate ultimaVenta) {
    }

    public record LineaCompra(String proveedor, int compras, int unidades, BigDecimal monto) {
    }

    private final ProductoRepositorio productos;
    private final MovimientoRepositorio movimientos;
    private final ProveedorRepositorio proveedores;
    private final Clock reloj;

    public ReporteServicio(ProductoRepositorio productos, MovimientoRepositorio movimientos,
                           ProveedorRepositorio proveedores, Clock reloj) {
        this.productos = productos;
        this.movimientos = movimientos;
        this.proveedores = proveedores;
        this.reloj = reloj;
    }

    /** Ventas entre dos fechas (ambas incluidas), de mayor a menor ingreso. */
    public ReporteVentas ventas(LocalDate desde, LocalDate hasta) {
        Map<String, String> nombres = nombresDeProductos();
        Map<String, List<Movimiento>> porProducto = movimientosEntre(desde, hasta).stream()
                .filter(m -> m.tipo() == TipoMovimiento.SALIDA)
                .collect(Collectors.groupingBy(Movimiento::codigoProducto, LinkedHashMap::new, Collectors.toList()));
        List<LineaVenta> lineas = porProducto.entrySet().stream()
                .map(e -> new LineaVenta(e.getKey(), nombres.getOrDefault(e.getKey(), e.getKey()),
                        e.getValue().stream().mapToInt(Movimiento::cantidad).sum(),
                        sumar(e.getValue(), Movimiento::importeVenta),
                        sumar(e.getValue(), Movimiento::importeCosto)))
                .sorted(Comparator.comparing(LineaVenta::ingresos).reversed().thenComparing(LineaVenta::codigo))
                .toList();
        return new ReporteVentas(desde, hasta, lineas);
    }

    /** Los productos con más unidades vendidas en el período. */
    public List<LineaVenta> masVendidos(LocalDate desde, LocalDate hasta, int limite) {
        return ventas(desde, hasta).lineas().stream()
                .sorted(Comparator.comparingInt(LineaVenta::unidades).reversed().thenComparing(LineaVenta::codigo))
                .limit(limite)
                .toList();
    }

    /** Ingresos y ganancia de cada día del período, incluidos los días sin ventas. */
    public List<VentaDiaria> ventasPorDia(LocalDate desde, LocalDate hasta) {
        Map<LocalDate, List<Movimiento>> porDia = movimientosEntre(desde, hasta).stream()
                .filter(m -> m.tipo() == TipoMovimiento.SALIDA)
                .collect(Collectors.groupingBy(m -> m.fecha().toLocalDate()));
        List<VentaDiaria> dias = new ArrayList<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            List<Movimiento> ventas = porDia.getOrDefault(dia, List.of());
            BigDecimal ingresos = sumar(ventas, Movimiento::importeVenta);
            dias.add(new VentaDiaria(dia, ingresos, ingresos.subtract(sumar(ventas, Movimiento::importeCosto))));
        }
        return dias;
    }

    /**
     * Productos activos con stock que no registran ventas en los últimos {@code dias} días:
     * mercadería inmovilizada. Los que nunca se vendieron aparecen primero.
     */
    public List<ProductoSinRotacion> sinRotacion(int dias) {
        if (dias <= 0) {
            throw new InventarioException("La cantidad de días debe ser mayor que cero.");
        }
        LocalDate limite = hoy().minusDays(dias);
        Map<String, LocalDate> ultimaVenta = new LinkedHashMap<>();
        for (Movimiento m : movimientos.listar()) {
            if (m.tipo() == TipoMovimiento.SALIDA) {
                ultimaVenta.merge(m.codigoProducto(), m.fecha().toLocalDate(),
                        (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        return productos.listar().stream()
                .filter(p -> p.isActivo() && p.getStock() > 0)
                .filter(p -> Optional.ofNullable(ultimaVenta.get(p.getCodigo())).map(f -> f.isBefore(limite))
                        .orElse(true))
                .map(p -> new ProductoSinRotacion(p, ultimaVenta.get(p.getCodigo())))
                .sorted(Comparator.comparing(ProductoSinRotacion::ultimaVenta,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
    }

    /** Compras (entradas) agrupadas por proveedor en el período, de mayor a menor monto. */
    public List<LineaCompra> comprasPorProveedor(LocalDate desde, LocalDate hasta) {
        Map<Long, String> nombres = proveedores.listar().stream()
                .collect(Collectors.toMap(Proveedor::id, Proveedor::nombre));
        Map<String, List<Movimiento>> porProveedor = movimientosEntre(desde, hasta).stream()
                .filter(m -> m.tipo() == TipoMovimiento.ENTRADA)
                .collect(Collectors.groupingBy(
                        m -> m.proveedorId() == null ? "Sin proveedor" : nombres.getOrDefault(m.proveedorId(), "?"),
                        LinkedHashMap::new, Collectors.toList()));
        return porProveedor.entrySet().stream()
                .map(e -> new LineaCompra(e.getKey(), e.getValue().size(),
                        e.getValue().stream().mapToInt(Movimiento::cantidad).sum(),
                        sumar(e.getValue(), Movimiento::importeCosto)))
                .sorted(Comparator.comparing(LineaCompra::monto).reversed())
                .toList();
    }

    // ---- Exportación ----

    public void exportarVentas(ReporteVentas reporte, Path archivo) {
        List<List<String>> filas = new ArrayList<>();
        for (LineaVenta l : reporte.lineas()) {
            filas.add(List.of(l.codigo(), l.nombre(), String.valueOf(l.unidades()), texto(l.ingresos()),
                    texto(l.costo()), texto(l.ganancia()), texto(l.margenPorcentaje())));
        }
        filas.add(List.of("TOTAL", "", String.valueOf(reporte.unidades()), texto(reporte.ingresos()),
                texto(reporte.costo()), texto(reporte.ganancia()), texto(reporte.margenPorcentaje())));
        escribir(archivo, List.of("Código", "Producto", "Unidades", "Ingresos", "Costo", "Ganancia", "Margen %"), filas);
    }

    public void exportarInventario(Path archivo) {
        List<List<String>> filas = productos.listar().stream()
                .filter(Producto::isActivo)
                .sorted(Comparator.comparing(Producto::getCodigo))
                .map(p -> List.of(p.getCodigo(), p.getNombre(), p.getCategoria(), texto(p.getPrecio()),
                        texto(p.getCosto()), String.valueOf(p.getStock()), String.valueOf(p.getStockMinimo()),
                        texto(p.costoEnStock()), texto(p.valorEnStock())))
                .toList();
        escribir(archivo, List.of("codigo", "nombre", "categoria", "precio", "costo", "stock", "stockMinimo",
                "valorAlCosto", "valorDeVenta"), filas);
    }

    // ---- Auxiliares ----

    private List<Movimiento> movimientosEntre(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new InventarioException("Indique las fechas del período.");
        }
        if (desde.isAfter(hasta)) {
            throw new InventarioException("La fecha inicial no puede ser posterior a la final.");
        }
        return movimientos.listar().stream()
                .filter(m -> {
                    LocalDate dia = m.fecha().toLocalDate();
                    return !dia.isBefore(desde) && !dia.isAfter(hasta);
                })
                .toList();
    }

    private Map<String, String> nombresDeProductos() {
        return productos.listar().stream().collect(Collectors.toMap(Producto::getCodigo, Producto::getNombre));
    }

    private LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    /** Días transcurridos desde la fecha hasta hoy. */
    public long diasDesde(LocalDate fecha) {
        return ChronoUnit.DAYS.between(fecha, hoy());
    }

    private static void escribir(Path archivo, List<String> cabecera, List<List<String>> filas) {
        try {
            ExportadorCsv.escribir(archivo, cabecera, filas);
        } catch (RuntimeException e) {
            throw new InventarioException("No se pudo exportar: " + e.getMessage(), e);
        }
    }

    private static String texto(BigDecimal valor) {
        return valor.toPlainString();
    }

    private static <T> BigDecimal sumar(List<T> elementos, Function<T, BigDecimal> valor) {
        return elementos.stream().map(valor).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static BigDecimal porcentaje(BigDecimal parte, BigDecimal total) {
        if (total.signum() == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return parte.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }
}
