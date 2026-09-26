package inventario;

import inventario.modelo.Producto;
import inventario.persistencia.MovimientoRepositorio;
import inventario.persistencia.ProductoRepositorio;
import inventario.persistencia.Transacciones;
import inventario.servicio.InventarioServicio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Random;

/**
 * Llena una base vacía con un minimarket de ejemplo y 60 días de actividad simulada, para demostraciones.
 * Usa una semilla fija: siempre genera los mismos datos.
 */
final class DatosDemo {

    private record Articulo(String codigo, String nombre, String categoria, String precio, int stock, int minimo,
                            int ventaMaximaDiaria) {
    }

    private static final List<Articulo> ARTICULOS = List.of(
            new Articulo("ARR-5K", "Arroz extra 5 kg", "Abarrotes", "21.90", 40, 10, 4),
            new Articulo("AZU-1K", "Azúcar rubia 1 kg", "Abarrotes", "4.20", 60, 15, 6),
            new Articulo("ACE-1L", "Aceite vegetal 1 L", "Abarrotes", "9.80", 35, 10, 3),
            new Articulo("FID-500", "Fideos spaghetti 500 g", "Abarrotes", "3.50", 80, 20, 8),
            new Articulo("LEN-500", "Lentejas 500 g", "Abarrotes", "5.60", 30, 8, 2),
            new Articulo("SAL-1K", "Sal de mesa 1 kg", "Abarrotes", "1.80", 25, 5, 2),
            new Articulo("ATU-170", "Atún en lata 170 g", "Conservas", "6.50", 48, 12, 4),
            new Articulo("LEC-400", "Leche evaporada 400 g", "Lácteos", "4.10", 72, 24, 9),
            new Articulo("YOG-1L", "Yogur de fresa 1 L", "Lácteos", "7.90", 20, 6, 3),
            new Articulo("GAS-15", "Gaseosa 1.5 L", "Bebidas", "7.50", 36, 12, 6),
            new Articulo("AGU-625", "Agua mineral 625 ml", "Bebidas", "1.50", 96, 24, 12),
            new Articulo("CAF-200", "Café instantáneo 200 g", "Bebidas", "18.90", 15, 4, 1),
            new Articulo("GAL-SOD", "Galletas de soda x6", "Snacks", "3.20", 50, 15, 5),
            new Articulo("DET-900", "Detergente en polvo 900 g", "Limpieza", "11.50", 24, 8, 2),
            new Articulo("JAB-TOC", "Jabón de tocador x3", "Limpieza", "6.90", 30, 10, 3),
            new Articulo("PAP-HIG", "Papel higiénico x4", "Limpieza", "5.40", 40, 12, 4));

    private DatosDemo() {
    }

    /** No hace nada si ya hay productos: nunca mezcla datos de ejemplo con datos reales. */
    static boolean cargarSiEstaVacia(ProductoRepositorio productos, MovimientoRepositorio movimientos,
                                     Transacciones transacciones) {
        if (!productos.listar().isEmpty()) {
            return false;
        }
        LocalDate hoy = LocalDate.now();
        RelojAjustable reloj = new RelojAjustable(hoy.minusDays(60).atTime(8, 0), ZoneId.systemDefault());
        InventarioServicio servicio = new InventarioServicio(productos, movimientos, transacciones, reloj);
        Random azar = new Random(2026);

        transacciones.ejecutar(() -> {
            for (Articulo a : ARTICULOS) {
                servicio.registrarProducto(a.codigo(), a.nombre(), a.categoria(), new BigDecimal(a.precio()),
                        a.stock(), a.minimo());
            }
            for (int dia = 59; dia >= 0; dia--) {
                LocalDate fecha = hoy.minusDays(dia);
                simularDia(servicio, reloj, azar, fecha, dia == 0);
            }
            // Un producto descontinuado, para mostrar la baja lógica.
            servicio.darDeBajaProducto("LEN-500");
        });
        return true;
    }

    private static void simularDia(InventarioServicio servicio, RelojAjustable reloj, Random azar, LocalDate fecha,
                                   boolean esHoy) {
        int hora = 8;
        for (Articulo a : ARTICULOS) {
            Producto p = servicio.obtener(a.codigo());
            int vendidas = Math.min(p.getStock(), azar.nextInt(a.ventaMaximaDiaria() + 1));
            // Algunos productos se venden poco: sirven para el reporte de productos sin rotación.
            if (vendidas > 0 && !a.codigo().equals("CAF-200") && !a.codigo().equals("SAL-1K")) {
                reloj.fijar(momento(fecha, hora++, azar));
                servicio.registrarSalida(a.codigo(), vendidas, "Venta mostrador");
            }
            p = servicio.obtener(a.codigo());
            // Se repone cuando baja del mínimo; hoy no, para que haya alertas de stock bajo.
            if (!esHoy && p.tieneStockBajo() && azar.nextInt(3) == 0) {
                reloj.fijar(fecha.atTime(22, azar.nextInt(60)));
                servicio.registrarEntrada(a.codigo(), a.stock(), "Compra a proveedor");
            }
        }
        if (fecha.getDayOfMonth() == 1) {
            Producto p = servicio.obtener("FID-500");
            if (p.getStock() > 2) {
                reloj.fijar(fecha.atTime(23, 0));
                servicio.ajustarStock("FID-500", p.getStock() - 2, "Conteo mensual: empaques dañados");
            }
        }
    }

    private static LocalDateTime momento(LocalDate fecha, int hora, Random azar) {
        return fecha.atTime(Math.min(hora, 21), azar.nextInt(60));
    }
}
