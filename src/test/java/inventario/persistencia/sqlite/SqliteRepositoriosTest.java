package inventario.persistencia.sqlite;

import inventario.modelo.Movimiento;
import inventario.modelo.Producto;
import inventario.modelo.TipoMovimiento;
import inventario.persistencia.PersistenciaException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

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
