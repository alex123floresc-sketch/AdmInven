package inventario.persistencia.sqlite;

import inventario.modelo.Movimiento;
import inventario.persistencia.ImportadorCsv;
import inventario.persistencia.MovimientoRepositorio;
import inventario.servicio.InventarioException;
import inventario.servicio.InventarioServicio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El servicio funcionando sobre la base de datos real, incluidas las transacciones. */
class InventarioConSqliteTest {

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
    void flujoCompletoDeStock() {
        InventarioServicio servicio = new InventarioServicio(productos, movimientos, bd);
        servicio.registrarProducto("A1", "Arroz", "Abarrotes", new BigDecimal("4.50"), 20, 5);
        servicio.registrarSalida("A1", 18, "Venta");
        servicio.registrarEntrada("A1", 10, "Compra");
        servicio.ajustarStock("A1", 11, "Conteo");

        assertEquals(11, productos.buscarPorCodigo("A1").orElseThrow().getStock());
        assertEquals(List.of(20, 2, 12, 11),
                servicio.historial("A1").stream().map(Movimiento::stockResultante).toList());
    }

    @Test
    void siFallaElMovimientoLaTransaccionRevierteElStockEnLaBase() {
        MovimientoRepositorio queFalla = new MovimientoRepositorio() {
            @Override
            public void registrar(Movimiento movimiento) {
                throw new IllegalStateException("disco lleno");
            }

            @Override
            public List<Movimiento> listar() {
                return List.of();
            }

            @Override
            public List<Movimiento> listarPorProducto(String codigoProducto) {
                return List.of();
            }
        };
        new InventarioServicio(productos, movimientos, bd)
                .registrarProducto("A1", "Arroz", "", BigDecimal.ONE, 20, 0);
        InventarioServicio servicio = new InventarioServicio(productos, queFalla, bd);

        assertThrows(InventarioException.class, () -> servicio.registrarSalida("A1", 5, ""));
        assertThrows(InventarioException.class, () -> servicio.registrarProducto("B2", "Sal", "", BigDecimal.ONE, 3, 0));

        assertEquals(20, productos.buscarPorCodigo("A1").orElseThrow().getStock());
        assertTrue(productos.buscarPorCodigo("B2").isEmpty());
    }

    @Test
    void importadorCopiaLosDatosCsvYOmiteMovimientosHuerfanos(@TempDir Path carpeta) throws IOException {
        Files.writeString(carpeta.resolve("productos.csv"), """
                codigo;nombre;categoria;precio;stock;stockMinimo;activo
                A1;Arroz;Abarrotes;4.50;12;5;true
                B2;Sal;General;1.00;0;0;false
                """);
        Files.writeString(carpeta.resolve("movimientos.csv"), """
                fecha;codigoProducto;tipo;cantidad;stockResultante;nota
                2026-09-25T10:00;A1;ENTRADA;15;15;Stock inicial
                2026-09-25T10:05;BORRADO;ENTRADA;1;1;
                2026-09-25T11:00;A1;SALIDA;3;12;Venta
                """);

        ImportadorCsv.Resultado r = ImportadorCsv.importar(carpeta, productos, movimientos, bd);

        assertEquals(2, r.productos());
        assertEquals(2, r.movimientos());
        assertEquals(1, r.advertencias().size());
        assertEquals(12, productos.buscarPorCodigo("A1").orElseThrow().getStock());
        assertEquals(2, movimientos.listarPorProducto("A1").size());
    }
}
