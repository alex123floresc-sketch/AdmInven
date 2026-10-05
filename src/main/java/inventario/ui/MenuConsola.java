package inventario.ui;

import inventario.modelo.Movimiento;
import inventario.modelo.Producto;
import inventario.modelo.Usuario;
import inventario.servicio.InventarioException;
import inventario.servicio.InventarioServicio;
import inventario.servicio.UsuarioServicio;

import java.io.Console;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MenuConsola {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final int INTENTOS_DE_ACCESO = 3;
    private static final Logger REGISTRO = Logger.getLogger(MenuConsola.class.getName());

    private final InventarioServicio servicio;
    private final UsuarioServicio usuarios;
    private final Scanner entrada;
    private final PrintStream salida;

    public MenuConsola(InventarioServicio servicio, UsuarioServicio usuarios, Scanner entrada, PrintStream salida) {
        this.servicio = servicio;
        this.usuarios = usuarios;
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

    private void mostrarMenu() {
        salida.println();
        salida.println("[" + usuarios.sesion().nombreUsuario() + "]");
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
        salida.println(" 0. Salir");
    }

    private boolean ejecutar(String opcion) {
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
        String nota = leerLinea("Nota (opcional): ");
        Producto p = servicio.registrarEntrada(actual.getCodigo(), cantidad, nota);
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

    // ---- Presentación ----

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
