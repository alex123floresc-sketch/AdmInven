package inventario.ui;

import inventario.Aplicacion;
import inventario.modelo.Movimiento;
import inventario.modelo.Permiso;
import inventario.modelo.Producto;
import inventario.modelo.Proveedor;
import inventario.modelo.Rol;
import inventario.modelo.Usuario;
import inventario.servicio.InventarioException;
import inventario.servicio.InventarioServicio;
import inventario.servicio.ProveedorServicio;
import inventario.servicio.ReporteServicio;
import inventario.servicio.RespaldoServicio;
import inventario.servicio.Sesion;
import inventario.servicio.UsuarioServicio;

import java.io.Console;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MenuConsola {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FORMATO_DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final int INTENTOS_DE_ACCESO = 3;
    private static final Logger REGISTRO = Logger.getLogger(MenuConsola.class.getName());

    private final InventarioServicio servicio;
    private final ProveedorServicio proveedores;
    private final ReporteServicio reportes;
    private final UsuarioServicio usuarios;
    private final RespaldoServicio respaldos;
    private final Sesion sesion;
    private final Scanner entrada;
    private final PrintStream salida;

    public MenuConsola(Aplicacion.Servicios servicios, Scanner entrada, PrintStream salida) {
        this.servicio = servicios.inventario();
        this.proveedores = servicios.proveedores();
        this.reportes = servicios.reportes();
        this.usuarios = servicios.usuarios();
        this.respaldos = servicios.respaldos();
        this.sesion = servicios.sesion();
        this.entrada = entrada;
        this.salida = salida;
    }

    public void iniciar() {
        salida.println("=== Administrador de Inventario ===");
        try {
            if (!identificarse()) {
                salida.println("Demasiados intentos fallidos.");
                return;
            }
            boolean continuar = true;
            while (continuar) {
                mostrarMenu();
                String opcion = leerLinea("Opción: ");
                try {
                    continuar = ejecutar(opcion);
                } catch (InventarioException e) {
                    salida.println("! " + e.getMessage());
                } catch (NoSuchElementException fin) {
                    throw fin;
                } catch (RuntimeException e) {
                    // Un fallo imprevisto (p. ej. de disco) no debe cerrar la aplicación.
                    REGISTRO.log(Level.SEVERE, "Error inesperado en la consola", e);
                    salida.println("! Error inesperado: " + e.getMessage());
                }
            }
        } catch (NoSuchElementException fin) {
            // Fin de la entrada estándar: se sale sin error.
        }
        salida.println("Hasta luego.");
    }

    /** Inicia sesión, o crea el administrador si es la primera vez. Devuelve false si no lo logra. */
    private boolean identificarse() {
        if (usuarios.requiereConfiguracionInicial()) {
            salida.println("Primera ejecución: cree el usuario administrador.");
            while (true) {
                try {
                    String usuario = leerObligatorio("Usuario: ");
                    String nombre = leerObligatorio("Nombre completo: ");
                    usuarios.crearAdministradorInicial(usuario, nombre, leerContrasena("Contraseña: "));
                    salida.println("Administrador creado.");
                    return true;
                } catch (InventarioException e) {
                    salida.println("! " + e.getMessage());
                }
            }
        }
        for (int intento = 1; intento <= INTENTOS_DE_ACCESO; intento++) {
            try {
                String usuario = leerObligatorio("Usuario: ");
                Usuario u = usuarios.iniciarSesion(usuario, leerContrasena("Contraseña: "));
                salida.println("Bienvenido(a), " + u.nombreCompleto() + " (" + u.rol() + ").");
                return true;
            } catch (InventarioException e) {
                salida.println("! " + e.getMessage());
            }
        }
        return false;
    }

    /** En una terminal real la contraseña no se muestra al escribirla. */
    private char[] leerContrasena(String mensaje) {
        Console consola = System.console();
        if (consola != null && consola.isTerminal()) {
            char[] leida = consola.readPassword(mensaje);
            if (leida == null) {
                throw new NoSuchElementException();
            }
            return leida;
        }
        return leerLinea(mensaje).toCharArray();
    }

    /** Proveedores, reportes, usuarios y copias solo se muestran a quien tiene el permiso. */
    private void mostrarMenu() {
        salida.println();
        salida.println("[" + sesion.nombreUsuario() + "]");
        salida.println(" 1. Listar productos");
        salida.println(" 2. Buscar productos");
        salida.println(" 3. Registrar producto");
        salida.println(" 4. Editar producto");
        salida.println(" 5. Dar de baja producto");
        salida.println(" 6. Registrar entrada de stock");
        salida.println(" 7. Registrar salida de stock");
        salida.println(" 8. Ajustar stock (conteo físico)");
        salida.println(" 9. Productos con stock bajo");
        salida.println("10. Historial de un producto");
        salida.println("11. Resumen del inventario");
        salida.println("12. Productos dados de baja");
        salida.println("13. Reactivar producto");
        if (sesion.puede(Permiso.GESTIONAR_PROVEEDORES)) {
            salida.println("14. Proveedores...");
        }
        if (sesion.puede(Permiso.VER_REPORTES)) {
            salida.println("15. Reportes...");
        }
        if (sesion.puede(Permiso.GESTIONAR_USUARIOS)) {
            salida.println("16. Usuarios...");
        }
        salida.println("17. Cambiar mi contraseña");
        if (sesion.puede(Permiso.RESPALDAR_DATOS)) {
            salida.println("18. Crear copia de seguridad");
        }
        salida.println(" 0. Salir");
    }

    private boolean ejecutar(String opcion) {
        Permiso necesario = switch (opcion) {
            case "14" -> Permiso.GESTIONAR_PROVEEDORES;
            case "15" -> Permiso.VER_REPORTES;
            case "16" -> Permiso.GESTIONAR_USUARIOS;
            case "18" -> Permiso.RESPALDAR_DATOS;
            default -> null;
        };
        if (necesario != null && !sesion.puede(necesario)) {
            salida.println("Opción no válida.");
            return true;
        }
        switch (opcion) {
            case "1" -> imprimirProductos(servicio.listarProductos());
            case "2" -> imprimirProductos(servicio.buscar(leerLinea("Texto a buscar: ")));
            case "3" -> registrarProducto();
            case "4" -> editarProducto();
            case "5" -> darDeBajaProducto();
            case "6" -> registrarEntrada();
            case "7" -> registrarSalida();
            case "8" -> ajustarStock();
            case "9" -> imprimirProductos(servicio.productosConStockBajo());
            case "10" -> imprimirHistorial();
            case "11" -> imprimirResumen();
            case "12" -> imprimirProductos(servicio.listarProductosDadosDeBaja());
            case "13" -> reactivarProducto();
            case "14" -> submenu("Proveedores", List.of("Listar proveedores", "Registrar proveedor",
                    "Editar proveedor", "Dar de baja proveedor", "Reactivar proveedor"), this::opcionProveedores);
            case "15" -> submenu("Reportes", List.of("Ventas de un período", "Más vendidos",
                    "Productos sin rotación", "Compras por proveedor", "Exportar ventas a CSV",
                    "Exportar inventario a CSV"), this::opcionReportes);
            case "16" -> submenu("Usuarios", List.of("Listar usuarios", "Crear usuario", "Cambiar rol",
                    "Restablecer contraseña", "Desactivar usuario", "Activar usuario"), this::opcionUsuarios);
            case "17" -> cambiarMiContrasena();
            case "18" -> crearCopiaDeSeguridad();
            case "0" -> {
                return false;
            }
            default -> salida.println("Opción no válida.");
        }
        return true;
    }

    // ---- Acciones ----

    private void registrarProducto() {
        String codigo = leerObligatorio("Código: ");
        servicio.validarCodigoDisponible(codigo);
        String nombre = leerObligatorio("Nombre: ");
        String categoria = leerLinea("Categoría [General]: ");
        BigDecimal precio = leerDecimal("Precio de venta: ", null);
        BigDecimal costo = leerDecimal("Costo unitario [0]: ", BigDecimal.ZERO);
        int stock = leerEntero("Stock inicial: ", 0, null);
        int minimo = leerEntero("Stock mínimo: ", 0, null);
        Producto p = servicio.registrarProducto(codigo, nombre, categoria, precio, costo, stock, minimo);
        salida.println("Producto " + p.getCodigo() + " registrado.");
    }

    private void editarProducto() {
        Producto actual = servicio.obtenerActivo(leerObligatorio("Código del producto: "));
        salida.println("Deje en blanco para conservar el valor actual.");
        String nombre = leerConValorPorDefecto("Nombre", actual.getNombre());
        String categoria = leerConValorPorDefecto("Categoría", actual.getCategoria());
        BigDecimal precio = leerDecimal("Precio [" + actual.getPrecio() + "]: ", actual.getPrecio());
        int minimo = leerEntero("Stock mínimo [" + actual.getStockMinimo() + "]: ", 0, actual.getStockMinimo());
        servicio.actualizarProducto(actual.getCodigo(), nombre, categoria, precio, minimo);
        salida.println("Producto actualizado.");
    }

    private void darDeBajaProducto() {
        Producto p = servicio.obtenerActivo(leerObligatorio("Código del producto: "));
        String confirmacion = leerLinea("¿Dar de baja \"" + p.getNombre() + "\"? Su historial se conservará. (s/N): ");
        if (confirmacion.equalsIgnoreCase("s")) {
            servicio.darDeBajaProducto(p.getCodigo());
            salida.println("Producto dado de baja. Puede reactivarlo con la opción 13.");
        } else {
            salida.println("Operación cancelada.");
        }
    }

    private void reactivarProducto() {
        String codigo = leerObligatorio("Código del producto: ");
        servicio.reactivarProducto(codigo);
        salida.println("Producto " + servicio.obtener(codigo).getCodigo() + " reactivado.");
    }

    private void registrarEntrada() {
        Producto actual = servicio.obtenerActivo(leerObligatorio("Código del producto: "));
        int cantidad = leerEntero("Cantidad que ingresa: ", 1, null);
        BigDecimal costo = leerDecimal("Costo unitario de compra [" + actual.getCosto() + "]: ", actual.getCosto());
        Long proveedor = elegirProveedor();
        String nota = leerLinea("Nota (opcional): ");
        Producto p = servicio.registrarEntrada(actual.getCodigo(), cantidad, costo, proveedor, nota);
        salida.println("Stock actual de " + p.getNombre() + ": " + p.getStock());
    }

    private void registrarSalida() {
        Producto actual = servicio.obtenerActivo(leerObligatorio("Código del producto: "));
        int cantidad = leerEntero("Cantidad que sale (hay " + actual.getStock() + "): ", 1, null);
        String nota = leerLinea("Nota (opcional): ");
        Producto p = servicio.registrarSalida(actual.getCodigo(), cantidad, nota);
        salida.println("Stock actual de " + p.getNombre() + ": " + p.getStock());
        if (p.tieneStockBajo()) {
            salida.println("! Atención: el producto está en o por debajo del stock mínimo (" + p.getStockMinimo() + ").");
        }
    }

    private void ajustarStock() {
        Producto actual = servicio.obtenerActivo(leerObligatorio("Código del producto: "));
        int nuevo = leerEntero("Stock contado (actual " + actual.getStock() + "): ", 0, null);
        String nota = leerLinea("Motivo del ajuste: ");
        Producto p = servicio.ajustarStock(actual.getCodigo(), nuevo, nota);
        salida.println("Stock actual de " + p.getNombre() + ": " + p.getStock());
    }

    /** Muestra los proveedores activos y devuelve el elegido, o null si se deja en blanco. */
    private Long elegirProveedor() {
        List<Proveedor> activos = proveedores.listarActivos();
        if (activos.isEmpty()) {
            return null;
        }
        activos.forEach(p -> salida.printf("  %3d  %s%n", p.id(), p.nombre()));
        while (true) {
            String valor = leerLinea("Proveedor (número, en blanco si no aplica): ");
            if (valor.isEmpty()) {
                return null;
            }
            try {
                long id = Long.parseLong(valor);
                if (activos.stream().anyMatch(p -> p.id() == id)) {
                    return id;
                }
            } catch (NumberFormatException e) {
                // Se vuelve a preguntar.
            }
            salida.println("Elija uno de los números de la lista.");
        }
    }

    private void cambiarMiContrasena() {
        char[] actual = leerContrasena("Contraseña actual: ");
        char[] nueva = leerContrasena("Contraseña nueva: ");
        usuarios.cambiarMiContrasena(actual, nueva);
        salida.println("Contraseña cambiada.");
    }

    private void crearCopiaDeSeguridad() {
        Path destino = leerArchivo(respaldos.nombreSugerido());
        respaldos.crearCopia(destino);
        salida.println("Copia guardada en " + destino.toAbsolutePath().normalize());
    }

    // ---- Submenús ----

    /** Repite el submenú hasta elegir 0; los errores se muestran sin salir de él. */
    private void submenu(String titulo, List<String> opciones, IntConsumer accion) {
        while (true) {
            salida.println();
            salida.println("--- " + titulo + " ---");
            for (int i = 0; i < opciones.size(); i++) {
                salida.printf("%2d. %s%n", i + 1, opciones.get(i));
            }
            salida.println(" 0. Volver");
            String opcion = leerLinea("Opción: ");
            if (opcion.equals("0")) {
                return;
            }
            int numero;
            try {
                numero = Integer.parseInt(opcion);
            } catch (NumberFormatException e) {
                numero = -1;
            }
            if (numero < 1 || numero > opciones.size()) {
                salida.println("Opción no válida.");
                continue;
            }
            try {
                accion.accept(numero);
            } catch (InventarioException | IllegalArgumentException e) {
                salida.println("! " + e.getMessage());
            }
        }
    }

    private void opcionProveedores(int opcion) {
        switch (opcion) {
            case 1 -> imprimirProveedores(proveedores.listarTodos());
            case 2 -> {
                Proveedor p = proveedores.registrar(leerObligatorio("Nombre: "),
                        leerLinea("RUC o DNI (opcional): "), leerLinea("Teléfono (opcional): "),
                        leerLinea("Correo (opcional): "));
                salida.println("Proveedor registrado con el número " + p.id() + ".");
            }
            case 3 -> {
                Proveedor actual = proveedores.obtener(leerEntero("Número del proveedor: ", 1, null));
                salida.println("Deje en blanco para conservar el valor actual.");
                proveedores.actualizar(actual.id(), leerConValorPorDefecto("Nombre", actual.nombre()),
                        leerConValorPorDefecto("RUC o DNI", actual.documento()),
                        leerConValorPorDefecto("Teléfono", actual.telefono()),
                        leerConValorPorDefecto("Correo", actual.email()));
                salida.println("Proveedor actualizado.");
            }
            case 4 -> {
                proveedores.darDeBaja(leerEntero("Número del proveedor: ", 1, null));
                salida.println("Proveedor dado de baja.");
            }
            default -> {
                proveedores.reactivar(leerEntero("Número del proveedor: ", 1, null));
                salida.println("Proveedor reactivado.");
            }
        }
    }

    private void opcionReportes(int opcion) {
        switch (opcion) {
            case 1 -> imprimirVentas(reportes.ventas(leerDesde(), leerHasta()));
            case 2 -> imprimirLineasVenta(reportes.masVendidos(leerDesde(), leerHasta(), 10));
            case 3 -> {
                int dias = leerEntero("Días sin ventas [30]: ", 1, 30);
                List<ReporteServicio.ProductoSinRotacion> lista = reportes.sinRotacion(dias);
                if (lista.isEmpty()) {
                    salida.println("Todos los productos con stock se vendieron en los últimos " + dias + " días.");
                }
                for (ReporteServicio.ProductoSinRotacion r : lista) {
                    salida.printf("%-10s %-28s stock %4d  %s%n", recortar(r.producto().getCodigo(), 10),
                            recortar(r.producto().getNombre(), 28), r.producto().getStock(),
                            r.ultimaVenta() == null ? "nunca vendido"
                                    : "última venta " + r.ultimaVenta().format(FORMATO_DIA));
                }
            }
            case 4 -> {
                List<ReporteServicio.LineaCompra> compras = reportes.comprasPorProveedor(leerDesde(), leerHasta());
                if (compras.isEmpty()) {
                    salida.println("No hubo compras en el período.");
                    return;
                }
                String formato = "%-30s %8s %9s %12s%n";
                salida.printf(formato, "PROVEEDOR", "COMPRAS", "UNIDADES", "MONTO");
                for (ReporteServicio.LineaCompra c : compras) {
                    salida.printf(formato, recortar(c.proveedor(), 30), c.compras(), c.unidades(),
                            c.monto().toPlainString());
                }
            }
            case 5 -> {
                ReporteServicio.ReporteVentas reporte = reportes.ventas(leerDesde(), leerHasta());
                Path archivo = leerArchivo("ventas-" + reporte.desde() + "-a-" + reporte.hasta() + ".csv");
                reportes.exportarVentas(reporte, archivo);
                salida.println("Exportado a " + archivo.toAbsolutePath().normalize());
            }
            default -> {
                Path archivo = leerArchivo("inventario-" + LocalDate.now() + ".csv");
                reportes.exportarInventario(archivo);
                salida.println("Exportado a " + archivo.toAbsolutePath().normalize());
            }
        }
    }

    private void opcionUsuarios(int opcion) {
        switch (opcion) {
            case 1 -> {
                String formato = "%-15s %-32s %-14s %s%n";
                salida.printf(formato, "USUARIO", "NOMBRE", "ROL", "ESTADO");
                for (Usuario u : usuarios.listar()) {
                    salida.printf(formato, u.nombreUsuario(), recortar(u.nombreCompleto(), 32), u.rol(),
                            u.activo() ? "activo" : "desactivado");
                }
            }
            case 2 -> {
                String usuario = leerObligatorio("Usuario: ");
                String nombre = leerObligatorio("Nombre completo: ");
                Rol rol = leerRol();
                Usuario u = usuarios.crearUsuario(usuario, nombre, rol, leerContrasena("Contraseña: "));
                salida.println("Usuario " + u.nombreUsuario() + " creado.");
            }
            case 3 -> {
                usuarios.cambiarRol(leerObligatorio("Usuario: "), leerRol());
                salida.println("Rol actualizado.");
            }
            case 4 -> {
                usuarios.restablecerContrasena(leerObligatorio("Usuario: "), leerContrasena("Contraseña nueva: "));
                salida.println("Contraseña restablecida.");
            }
            case 5 -> {
                usuarios.desactivar(leerObligatorio("Usuario: "));
                salida.println("Usuario desactivado.");
            }
            default -> {
                usuarios.activar(leerObligatorio("Usuario: "));
                salida.println("Usuario activado.");
            }
        }
    }

    // ---- Presentación ----

    private void imprimirProveedores(List<Proveedor> lista) {
        if (lista.isEmpty()) {
            salida.println("No hay proveedores registrados.");
            return;
        }
        String formato = "%4s %-28s %-12s %-14s %-26s %s%n";
        salida.printf(formato, "N.º", "NOMBRE", "RUC/DNI", "TELÉFONO", "CORREO", "ESTADO");
        for (Proveedor p : lista) {
            salida.printf(formato, p.id(), recortar(p.nombre(), 28), p.documento(), recortar(p.telefono(), 14),
                    recortar(p.email(), 26), p.activo() ? "activo" : "de baja");
        }
    }

    private void imprimirVentas(ReporteServicio.ReporteVentas reporte) {
        salida.println("Ventas del " + reporte.desde().format(FORMATO_DIA) + " al "
                + reporte.hasta().format(FORMATO_DIA));
        if (reporte.lineas().isEmpty()) {
            salida.println("No hubo ventas en el período.");
            return;
        }
        imprimirLineasVenta(reporte.lineas());
        salida.println("-".repeat(88));
        salida.printf("%-39s %8d %12s %12s %12s %7s%n", "TOTAL", reporte.unidades(),
                reporte.ingresos().toPlainString(), reporte.costo().toPlainString(),
                reporte.ganancia().toPlainString(), reporte.margenPorcentaje().toPlainString() + "%");
    }

    private void imprimirLineasVenta(List<ReporteServicio.LineaVenta> lineas) {
        String formato = "%-10s %-28s %8s %12s %12s %12s %7s%n";
        salida.printf(formato, "CÓDIGO", "PRODUCTO", "UNIDADES", "INGRESOS", "COSTO", "GANANCIA", "MARGEN");
        for (ReporteServicio.LineaVenta l : lineas) {
            salida.printf(formato, recortar(l.codigo(), 10), recortar(l.nombre(), 28), l.unidades(),
                    l.ingresos().toPlainString(), l.costo().toPlainString(), l.ganancia().toPlainString(),
                    l.margenPorcentaje().toPlainString() + "%");
        }
    }

    private void imprimirProductos(List<Producto> lista) {
        if (lista.isEmpty()) {
            salida.println("No hay productos para mostrar.");
            return;
        }
        String formato = "%-10s %-28s %-15s %10s %7s %7s%n";
        salida.printf(formato, "CÓDIGO", "NOMBRE", "CATEGORÍA", "PRECIO", "STOCK", "MÍNIMO");
        salida.println("-".repeat(82));
        for (Producto p : lista) {
            salida.printf(formato, recortar(p.getCodigo(), 10), recortar(p.getNombre(), 28),
                    recortar(p.getCategoria(), 15), p.getPrecio(),
                    p.getStock() + (p.tieneStockBajo() ? "*" : ""), p.getStockMinimo());
        }
        salida.println(lista.size() + " producto(s). (*) stock bajo.");
    }

    private void imprimirHistorial() {
        Producto p = servicio.obtener(leerObligatorio("Código del producto: "));
        List<Movimiento> historial = servicio.historial(p.getCodigo());
        salida.println("Historial de " + p.getCodigo() + " - " + p.getNombre() + (p.isActivo() ? "" : " (dado de baja)"));
        if (historial.isEmpty()) {
            salida.println("Sin movimientos registrados.");
            return;
        }
        imprimirMovimientos(historial);
    }

    private void imprimirResumen() {
        List<Producto> todos = servicio.listarProductos();
        salida.println("Productos activos     : " + todos.size());
        salida.println("Unidades en stock     : " + servicio.unidadesTotales());
        salida.println("Valor del inventario  : " + servicio.valorTotalInventario().toPlainString());
        salida.println("Productos stock bajo  : " + servicio.productosConStockBajo().size());
        List<Movimiento> recientes = servicio.ultimosMovimientos(5);
        if (!recientes.isEmpty()) {
            salida.println();
            salida.println("Últimos movimientos:");
            imprimirMovimientos(recientes);
        }
    }

    private void imprimirMovimientos(List<Movimiento> lista) {
        String formato = "%-16s %-10s %-8s %8s %8s  %s%n";
        salida.printf(formato, "FECHA", "CÓDIGO", "TIPO", "CANTIDAD", "STOCK", "NOTA");
        for (Movimiento m : lista) {
            salida.printf(formato, m.fecha().format(FORMATO_FECHA), recortar(m.codigoProducto(), 10),
                    m.tipo(), m.cantidad(), m.stockResultante(), m.nota());
        }
    }

    private static String recortar(String texto, int max) {
        return texto.length() <= max ? texto : texto.substring(0, max - 1) + "…";
    }

    // ---- Lectura de datos ----

    private String leerLinea(String mensaje) {
        salida.print(mensaje);
        salida.flush();
        return entrada.nextLine().strip();
    }

    private String leerObligatorio(String mensaje) {
        while (true) {
            String valor = leerLinea(mensaje);
            if (!valor.isEmpty()) {
                return valor;
            }
            salida.println("Este campo es obligatorio.");
        }
    }

    private String leerConValorPorDefecto(String etiqueta, String actual) {
        String valor = leerLinea(etiqueta + " [" + actual + "]: ");
        return valor.isEmpty() ? actual : valor;
    }

    /** Inicio del período; por omisión, los últimos 30 días. */
    private LocalDate leerDesde() {
        return leerFecha("Desde", LocalDate.now().minusDays(29));
    }

    private LocalDate leerHasta() {
        return leerFecha("Hasta", LocalDate.now());
    }

    /** Fecha en formato dd/mm/aaaa; en blanco devuelve {@code porDefecto}. */
    private LocalDate leerFecha(String etiqueta, LocalDate porDefecto) {
        while (true) {
            String valor = leerLinea(etiqueta + " (dd/mm/aaaa) [" + porDefecto.format(FORMATO_DIA) + "]: ");
            if (valor.isEmpty()) {
                return porDefecto;
            }
            try {
                return LocalDate.parse(valor, FORMATO_DIA);
            } catch (DateTimeParseException e) {
                salida.println("Escriba la fecha como dd/mm/aaaa, por ejemplo 05/10/2026.");
            }
        }
    }

    private Path leerArchivo(String porDefecto) {
        String valor = leerLinea("Archivo [" + porDefecto + "]: ");
        return Path.of(valor.isEmpty() ? porDefecto : valor);
    }

    private Rol leerRol() {
        while (true) {
            String valor = leerLinea("Rol (1 = Administrador, 2 = Vendedor): ");
            if (valor.equals("1")) {
                return Rol.ADMINISTRADOR;
            }
            if (valor.equals("2")) {
                return Rol.VENDEDOR;
            }
            salida.println("Escriba 1 o 2.");
        }
    }

    /** Si {@code porDefecto} no es null, una respuesta vacía lo devuelve. */
    private int leerEntero(String mensaje, int minimo, Integer porDefecto) {
        while (true) {
            String valor = leerLinea(mensaje);
            if (valor.isEmpty() && porDefecto != null) {
                return porDefecto;
            }
            try {
                int numero = Integer.parseInt(valor);
                if (numero >= minimo) {
                    return numero;
                }
                salida.println("Ingrese un número mayor o igual a " + minimo + ".");
            } catch (NumberFormatException e) {
                salida.println("Ingrese un número entero válido.");
            }
        }
    }

    /** Acepta coma o punto como separador decimal. */
    private BigDecimal leerDecimal(String mensaje, BigDecimal porDefecto) {
        while (true) {
            String valor = leerLinea(mensaje);
            if (valor.isEmpty() && porDefecto != null) {
                return porDefecto;
            }
            try {
                BigDecimal numero = new BigDecimal(valor.replace(',', '.'));
                if (numero.signum() >= 0) {
                    return numero;
                }
                salida.println("El valor no puede ser negativo.");
            } catch (NumberFormatException e) {
                salida.println("Ingrese un número válido (por ejemplo 12.50).");
            }
        }
    }
}
