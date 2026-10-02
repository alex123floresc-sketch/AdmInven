package inventario.persistencia.sqlite;

import inventario.modelo.Movimiento;
import inventario.modelo.Producto;
import inventario.modelo.Proveedor;
import inventario.modelo.Rol;
import inventario.modelo.TipoMovimiento;
import inventario.modelo.Usuario;
import inventario.persistencia.PersistenciaException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteRepositoriosTest {

    private BaseDeDatos bd;
    private SqliteProductoRepositorio productos;
    private SqliteMovimientoRepositorio movimientos;

    @BeforeEach
    void abrir() {
        bd = BaseDeDatos.enMemoria();
        productos = new SqliteProductoRepositorio(bd);
        movimientos = new SqliteMovimientoRepositorio(bd);
    }

    @AfterEach
    void cerrar() {
        bd.close();
    }

    @Test
    void guardarYLeerProductoConservaTodosLosCampos() {
        Producto p = new Producto("lap-01", "Laptop; 14\"", "Cómputo", new BigDecimal("2500.5"), 3, 1);
        p.setActivo(false);

        productos.guardar(p);

        Producto leido = productos.buscarPorCodigo("LAP-01").orElseThrow();
        assertEquals("LAP-01", leido.getCodigo());
        assertEquals("Laptop; 14\"", leido.getNombre());
        assertEquals("Cómputo", leido.getCategoria());
        assertEquals(new BigDecimal("2500.50"), leido.getPrecio());
        assertEquals(3, leido.getStock());
        assertEquals(1, leido.getStockMinimo());
        assertFalse(leido.isActivo());
    }

    @Test
    void guardarConCodigoExistenteActualiza() {
        productos.guardar(new Producto("A1", "Arroz", "", BigDecimal.ONE, 1, 0));
        productos.guardar(new Producto("A1", "Arroz integral", "Granos", BigDecimal.TEN, 7, 2));

        List<Producto> todos = productos.listar();

        assertEquals(1, todos.size());
        assertEquals("Arroz integral", todos.getFirst().getNombre());
        assertEquals(7, todos.getFirst().getStock());
    }

    @Test
    void listarOrdenaPorCodigoYEliminarQuitaElProducto() {
        productos.guardar(new Producto("B", "Dos", "", BigDecimal.ONE, 0, 0));
        productos.guardar(new Producto("A", "Uno", "", BigDecimal.ONE, 0, 0));

        assertEquals(List.of("A", "B"), productos.listar().stream().map(Producto::getCodigo).toList());
        assertTrue(productos.eliminar("a"));
        assertFalse(productos.eliminar("a"));
        assertTrue(productos.buscarPorCodigo("A").isEmpty());
    }

    @Test
    void movimientosSeGuardanEnOrdenYSeFiltranPorProducto() {
        productos.guardar(new Producto("A1", "Arroz", "", BigDecimal.ONE, 0, 0));
        productos.guardar(new Producto("B2", "Sal", "", BigDecimal.ONE, 0, 0));
        Movimiento m1 = new Movimiento(LocalDateTime.of(2026, 9, 25, 10, 0), "A1", TipoMovimiento.ENTRADA, 5, 5, "Compra");
        Movimiento m2 = new Movimiento(LocalDateTime.of(2026, 9, 25, 10, 0, 30), "B2", TipoMovimiento.AJUSTE, -1, 0, "");
        Movimiento m3 = new Movimiento(LocalDateTime.of(2026, 9, 25, 11, 0), "A1", TipoMovimiento.SALIDA, 2, 3, "Venta");

        movimientos.registrar(m1);
        movimientos.registrar(m2);
        movimientos.registrar(m3);

        assertEquals(List.of(m1, m2, m3), movimientos.listar());
        assertEquals(List.of(m1, m3), movimientos.listarPorProducto("a1"));
    }

    @Test
    void consultasPorFechaIncluyenAmbosExtremosYDanLaUltimaVenta() {
        productos.guardar(new Producto("A1", "Arroz", "", BigDecimal.ONE, 0, 0));
        Movimiento antes = new Movimiento(LocalDateTime.of(2026, 9, 9, 23, 59, 59), "A1", TipoMovimiento.ENTRADA, 9, 9, "");
        Movimiento inicio = new Movimiento(LocalDateTime.of(2026, 9, 10, 0, 0), "A1", TipoMovimiento.SALIDA, 1, 8, "");
        Movimiento fin = new Movimiento(LocalDateTime.of(2026, 9, 12, 23, 59, 59), "A1", TipoMovimiento.SALIDA, 1, 7, "");
        Movimiento despues = new Movimiento(LocalDateTime.of(2026, 9, 13, 0, 0), "A1", TipoMovimiento.AJUSTE, -1, 6, "");
        List.of(antes, inicio, fin, despues).forEach(movimientos::registrar);

        assertEquals(List.of(inicio, fin),
                movimientos.listarEntre(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12)));
        assertEquals(Map.of("A1", LocalDate.of(2026, 9, 12)), movimientos.ultimaVentaPorProducto());
    }

    @Test
    void costoYDatosEconomicosDelMovimientoSeConservan() {
        productos.guardar(new Producto("A1", "Arroz", "", new BigDecimal("5"), new BigDecimal("3.755"), 0, 0));
        SqliteProveedorRepositorio proveedores = new SqliteProveedorRepositorio(bd);
        long andina = proveedores.guardar(new Proveedor(null, "Andina", "20512345678", "", "", true)).id();
        Movimiento compra = new Movimiento(LocalDateTime.of(2026, 9, 25, 9, 0), "A1", TipoMovimiento.ENTRADA, 10, 10,
                "Factura 001", BigDecimal.ZERO, new BigDecimal("3.70"), andina);

        movimientos.registrar(compra);

        assertEquals(new BigDecimal("3.76"), productos.buscarPorCodigo("A1").orElseThrow().getCosto());
        assertEquals(List.of(compra), movimientos.listar());
    }

    @Test
    void proveedoresSeInsertanActualizanYNoRepitenNombre() {
        SqliteProveedorRepositorio proveedores = new SqliteProveedorRepositorio(bd);
        Proveedor andina = proveedores.guardar(new Proveedor(null, "Andina", "", "", "", true));
        proveedores.guardar(new Proveedor(null, "Bebidas", "", "", "", true));

        proveedores.guardar(new Proveedor(andina.id(), "Andina SAC", "", "999", "", false));

        assertEquals(List.of("Andina SAC", "Bebidas"), proveedores.listar().stream().map(Proveedor::nombre).toList());
        assertFalse(proveedores.buscarPorId(andina.id()).orElseThrow().activo());
        assertThrows(PersistenciaException.class,
                () -> proveedores.guardar(new Proveedor(null, "bebidas", "", "", "", true)));
    }

    @Test
    void usuariosSeGuardanYActualizanPorNombre() {
        SqliteUsuarioRepositorio usuarios = new SqliteUsuarioRepositorio(bd);
        usuarios.guardar(new Usuario("luis", "Luis Quispe", Rol.VENDEDOR, "hash-1", true));
        usuarios.guardar(new Usuario("admin", "Ana Torres", Rol.ADMINISTRADOR, "hash-2", true));

        usuarios.guardar(new Usuario("luis", "Luis Quispe", Rol.ADMINISTRADOR, "hash-3", false));

        assertEquals(List.of("admin", "luis"), usuarios.listar().stream().map(Usuario::nombreUsuario).toList());
        Usuario luis = usuarios.buscar("luis").orElseThrow();
        assertEquals(Rol.ADMINISTRADOR, luis.rol());
        assertEquals("hash-3", luis.contrasena());
        assertFalse(luis.activo());
    }

    @Test
    void movimientoConservaElUsuarioQueLoRegistro() {
        productos.guardar(new Producto("A1", "Arroz", "", BigDecimal.ONE, 0, 0));
        Movimiento m = new Movimiento(LocalDateTime.of(2026, 9, 25, 9, 0), "A1", TipoMovimiento.SALIDA, 1, 0, "",
                BigDecimal.ONE, BigDecimal.ONE, null, "luis");

        movimientos.registrar(m);

        assertEquals("luis", movimientos.listar().getFirst().usuario());
    }

    @Test
    void movimientoDeProductoInexistenteViolaLaClaveForanea() {
        Movimiento huerfano = new Movimiento(LocalDateTime.now(), "NADA", TipoMovimiento.ENTRADA, 1, 1, "");

        assertThrows(PersistenciaException.class, () -> movimientos.registrar(huerfano));
    }

    @Test
    void transaccionRevierteTodoSiAlgoFalla() {
        productos.guardar(new Producto("A1", "Arroz", "", BigDecimal.ONE, 10, 0));

        assertThrows(IllegalStateException.class, () -> bd.ejecutar(() -> {
            productos.guardar(new Producto("A1", "Arroz", "", BigDecimal.ONE, 3, 0));
            productos.guardar(new Producto("B2", "Sal", "", BigDecimal.ONE, 1, 0));
            throw new IllegalStateException("fallo simulado");
        }));

        assertEquals(10, productos.buscarPorCodigo("A1").orElseThrow().getStock());
        assertTrue(productos.buscarPorCodigo("B2").isEmpty());
    }

    @Test
    void transaccionAnidadaSeUneALaExterior() {
        assertThrows(IllegalStateException.class, () -> bd.ejecutar(() -> {
            bd.ejecutar(() -> productos.guardar(new Producto("A1", "Arroz", "", BigDecimal.ONE, 1, 0)));
            throw new IllegalStateException("fallo después de la transacción interna");
        }));

        assertTrue(productos.listar().isEmpty());
    }

    @Test
    void migracionesSeAplicanUnaSolaVezAlReabrirElArchivo(@TempDir Path carpeta) {
        Path archivo = carpeta.resolve("inventario.db");
        try (BaseDeDatos primera = BaseDeDatos.abrir(archivo)) {
            new SqliteProductoRepositorio(primera).guardar(new Producto("A1", "Arroz", "", BigDecimal.ONE, 4, 0));
        }

        try (BaseDeDatos segunda = BaseDeDatos.abrir(archivo)) {
            assertTrue(segunda.versionEsquema() >= 1);
            assertEquals(4, new SqliteProductoRepositorio(segunda).buscarPorCodigo("A1").orElseThrow().getStock());
        }
    }
}
