package inventario;

import inventario.modelo.Producto;
import inventario.persistencia.MovimientoRepositorio;
import inventario.persistencia.ProductoRepositorio;
import inventario.persistencia.ProveedorRepositorio;
import inventario.modelo.Rol;
import inventario.persistencia.Transacciones;
import inventario.persistencia.UsuarioRepositorio;
import inventario.servicio.InventarioServicio;
import inventario.servicio.ProveedorServicio;
import inventario.servicio.Sesion;
import inventario.servicio.UsuarioServicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private record Articulo(String codigo, String nombre, String categoria, String precio, String costo, int stock,
                            int minimo, int ventaMaximaDiaria, int proveedor) {
    }

    private static final List<Articulo> ARTICULOS = List.of(
            new Articulo("ARR-5K", "Arroz extra 5 kg", "Abarrotes", "21.90", "17.50", 40, 10, 4, 0),
            new Articulo("AZU-1K", "Azúcar rubia 1 kg", "Abarrotes", "4.20", "3.30", 60, 15, 6, 0),
            new Articulo("ACE-1L", "Aceite vegetal 1 L", "Abarrotes", "9.80", "7.60", 35, 10, 3, 0),
            new Articulo("FID-500", "Fideos spaghetti 500 g", "Abarrotes", "3.50", "2.60", 80, 20, 8, 0),
            new Articulo("LEN-500", "Lentejas 500 g", "Abarrotes", "5.60", "4.20", 30, 8, 2, 0),
            new Articulo("SAL-1K", "Sal de mesa 1 kg", "Abarrotes", "1.80", "1.10", 25, 5, 2, 0),
            new Articulo("ATU-170", "Atún en lata 170 g", "Conservas", "6.50", "4.90", 48, 12, 4, 0),
            new Articulo("LEC-400", "Leche evaporada 400 g", "Lácteos", "4.10", "3.40", 72, 24, 9, 1),
            new Articulo("YOG-1L", "Yogur de fresa 1 L", "Lácteos", "7.90", "6.10", 20, 6, 3, 1),
            new Articulo("GAS-15", "Gaseosa 1.5 L", "Bebidas", "7.50", "5.60", 36, 12, 6, 2),
            new Articulo("AGU-625", "Agua mineral 625 ml", "Bebidas", "1.50", "0.90", 96, 24, 12, 2),
            new Articulo("CAF-200", "Café instantáneo 200 g", "Bebidas", "18.90", "14.80", 15, 4, 1, 0),
            new Articulo("GAL-SOD", "Galletas de soda x6", "Snacks", "3.20", "2.40", 50, 15, 5, 0),
            new Articulo("DET-900", "Detergente en polvo 900 g", "Limpieza", "11.50", "8.90", 24, 8, 2, 3),
            new Articulo("JAB-TOC", "Jabón de tocador x3", "Limpieza", "6.90", "5.10", 30, 10, 3, 3),
            new Articulo("PAP-HIG", "Papel higiénico x4", "Limpieza", "5.40", "4.00", 40, 12, 4, 3));

    private DatosDemo() {
    }

    /**
     * No hace nada si ya hay productos o usuarios: nunca mezcla datos de ejemplo con datos reales.
     * Crea los usuarios de {@link Aplicacion#CREDENCIALES_DEMO}; las ventas quedan a nombre del vendedor y las
     * compras y ajustes a nombre del administrador.
     */
    static boolean cargarSiEstaVacia(ProductoRepositorio productos, MovimientoRepositorio movimientos,
                                     ProveedorRepositorio repoProveedores, UsuarioRepositorio repoUsuarios,
                                     Transacciones transacciones) {
        if (!productos.listar().isEmpty() || !repoUsuarios.listar().isEmpty()) {
            return false;
        }
        LocalDate hoy = LocalDate.now();
        RelojAjustable reloj = new RelojAjustable(hoy.minusDays(60).atTime(8, 0), ZoneId.systemDefault());
        Sesion sesionAdmin = Sesion.nueva();
        Sesion sesionVendedor = Sesion.nueva();
        UsuarioServicio usuarios = new UsuarioServicio(repoUsuarios, sesionAdmin);
        usuarios.crearAdministradorInicial("admin", "Ana Torres (administradora)", "admin123".toCharArray());
        usuarios.crearUsuario("vendedor", "Luis Quispe (vendedor)", Rol.VENDEDOR, "vendedor123".toCharArray());
        new UsuarioServicio(repoUsuarios, sesionVendedor).iniciarSesion("vendedor", "vendedor123".toCharArray());

        InventarioServicio servicio = new InventarioServicio(productos, movimientos, transacciones, reloj,
                sesionAdmin);
        InventarioServicio mostrador = new InventarioServicio(productos, movimientos, transacciones, reloj,
                sesionVendedor);
        ProveedorServicio proveedores = new ProveedorServicio(repoProveedores, sesionAdmin);
        Random azar = new Random(2026);

        transacciones.ejecutar(() -> {
            List<Long> idsProveedores = List.of(
                    proveedores.registrar("Distribuidora Andina SAC", "20512345678", "01 425 7788",
                            "ventas@andina.example").id(),
                    proveedores.registrar("Lácteos del Sur EIRL", "20698765432", "054 223 114", "").id(),
                    proveedores.registrar("Bebidas Norte SA", "20455512399", "", "pedidos@bebidasnorte.example").id(),
                    proveedores.registrar("Limpieza Total", "10456789012", "987 654 321", "").id());
            for (Articulo a : ARTICULOS) {
                servicio.registrarProducto(a.codigo(), a.nombre(), a.categoria(), new BigDecimal(a.precio()),
                        new BigDecimal(a.costo()), a.stock(), a.minimo());
            }
            for (int dia = 59; dia >= 0; dia--) {
                LocalDate fecha = hoy.minusDays(dia);
                simularDia(servicio, mostrador, idsProveedores, reloj, azar, fecha, dia == 0);
            }
            // Un producto descontinuado, para mostrar la baja lógica.
            servicio.darDeBajaProducto("LEN-500");
        });
        return true;
    }

    private static void simularDia(InventarioServicio servicio, InventarioServicio mostrador, List<Long> proveedores,
                                   RelojAjustable reloj, Random azar, LocalDate fecha, boolean esHoy) {
        int hora = 8;
        for (Articulo a : ARTICULOS) {
            Producto p = servicio.obtener(a.codigo());
            int vendidas = Math.min(p.getStock(), azar.nextInt(a.ventaMaximaDiaria() + 1));
            // Algunos productos se venden poco: sirven para el reporte de productos sin rotación.
            if (vendidas > 0 && !a.codigo().equals("CAF-200") && !a.codigo().equals("SAL-1K")) {
                reloj.fijar(momento(fecha, hora++, azar));
                mostrador.registrarSalida(a.codigo(), vendidas, "Venta mostrador");
            }
            p = servicio.obtener(a.codigo());
            // Se repone cuando baja del mínimo; hoy no, para que haya alertas de stock bajo.
            if (!esHoy && p.tieneStockBajo() && azar.nextInt(3) == 0) {
                reloj.fijar(fecha.atTime(22, azar.nextInt(60)));
                // El precio de compra varía hasta ±5 % respecto al costo de referencia.
                BigDecimal costo = new BigDecimal(a.costo())
                        .multiply(BigDecimal.valueOf(95 + azar.nextInt(11))).movePointLeft(2)
                        .setScale(2, RoundingMode.HALF_UP);
                servicio.registrarEntrada(a.codigo(), a.stock(), costo, proveedores.get(a.proveedor()),
                        "Compra a proveedor");
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
