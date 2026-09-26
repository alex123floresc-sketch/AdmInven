package inventario.persistencia;

import inventario.modelo.Movimiento;
import inventario.modelo.Producto;
import inventario.modelo.TipoMovimiento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoriosArchivoTest {

    @TempDir
    Path carpeta;

    @Test
    void archivoInexistenteEmpiezaVacio() {
        assertTrue(new ArchivoProductoRepositorio(carpeta.resolve("productos.csv")).listar().isEmpty());
        assertTrue(new ArchivoMovimientoRepositorio(carpeta.resolve("movimientos.csv")).listar().isEmpty());
    }

    @Test
    void productosSobrevivenAlRecargarDesdeDisco() {
        Path archivo = carpeta.resolve("productos.csv");
        ArchivoProductoRepositorio repo = new ArchivoProductoRepositorio(archivo);
        repo.guardar(new Producto("LAP-01", "Laptop; 14\"", "Cómputo", new BigDecimal("2500.5"), 3, 1));
        repo.guardar(new Producto("MOU-01", "Mouse", "Periféricos", new BigDecimal("35"), 10, 2));

        ArchivoProductoRepositorio recargado = new ArchivoProductoRepositorio(archivo);

        assertEquals(2, recargado.listar().size());
        Producto laptop = recargado.buscarPorCodigo("lap-01").orElseThrow();
        assertEquals("Laptop; 14\"", laptop.getNombre());
        assertEquals("Cómputo", laptop.getCategoria());
        assertEquals(new BigDecimal("2500.50"), laptop.getPrecio());
        assertEquals(3, laptop.getStock());
        assertEquals(1, laptop.getStockMinimo());
    }

    @Test
    void guardarConMismoCodigoReemplaza() {
        Path archivo = carpeta.resolve("productos.csv");
        ArchivoProductoRepositorio repo = new ArchivoProductoRepositorio(archivo);
        repo.guardar(new Producto("A1", "Arroz", "", BigDecimal.ONE, 1, 0));
        repo.guardar(new Producto("a1", "Arroz integral", "", BigDecimal.TEN, 5, 0));

        List<Producto> productos = new ArchivoProductoRepositorio(archivo).listar();

        assertEquals(1, productos.size());
        assertEquals("Arroz integral", productos.getFirst().getNombre());
    }

    @Test
    void eliminarSePersiste() {
        Path archivo = carpeta.resolve("productos.csv");
        ArchivoProductoRepositorio repo = new ArchivoProductoRepositorio(archivo);
        repo.guardar(new Producto("A1", "Arroz", "", BigDecimal.ONE, 1, 0));

        assertTrue(repo.eliminar("a1"));
        assertFalse(repo.eliminar("a1"));
        assertTrue(new ArchivoProductoRepositorio(archivo).listar().isEmpty());
    }

    @Test
    void movimientosSobrevivenAlRecargarYSeFiltranPorProducto() {
        Path archivo = carpeta.resolve("movimientos.csv");
        ArchivoMovimientoRepositorio repo = new ArchivoMovimientoRepositorio(archivo);
        Movimiento entrada = new Movimiento(LocalDateTime.of(2026, 9, 25, 10, 0), "A1",
                TipoMovimiento.ENTRADA, 10, 10, "Compra; factura \"001\"");
        Movimiento ajuste = new Movimiento(LocalDateTime.of(2026, 9, 25, 11, 30), "B2",
                TipoMovimiento.AJUSTE, -2, 3, "");
        repo.registrar(entrada);
        repo.registrar(ajuste);

        ArchivoMovimientoRepositorio recargado = new ArchivoMovimientoRepositorio(archivo);

        assertEquals(List.of(entrada, ajuste), recargado.listar());
        assertEquals(List.of(entrada), recargado.listarPorProducto("a1"));
    }

    @Test
    void registrarMovimientoAgregaAlFinalSinReescribirLoExistente() throws IOException {
        Path archivo = carpeta.resolve("movimientos.csv");
        // Las comillas innecesarias en "Compra" desaparecerían si el archivo se reescribiera.
        String existente = "fecha;codigoProducto;tipo;cantidad;stockResultante;nota\n"
                + "2026-09-25T10:00;A1;ENTRADA;5;5;\"Compra\"\n";
        Files.writeString(archivo, existente);
        ArchivoMovimientoRepositorio repo = new ArchivoMovimientoRepositorio(archivo);

        repo.registrar(new Movimiento(LocalDateTime.of(2026, 9, 25, 11, 0), "A1", TipoMovimiento.SALIDA, 2, 3, "Venta"));

        assertTrue(Files.readString(archivo).startsWith(existente));
        List<Movimiento> recargados = new ArchivoMovimientoRepositorio(archivo).listar();
        assertEquals(2, recargados.size());
        assertEquals("Venta", recargados.get(1).nota());
    }

    @Test
    void registrarMovimientoRespetaArchivoSinSaltoDeLineaFinal() throws IOException {
        Path archivo = carpeta.resolve("movimientos.csv");
        Files.writeString(archivo, "fecha;codigoProducto;tipo;cantidad;stockResultante;nota\n"
                + "2026-09-25T10:00;A1;ENTRADA;5;5;");
        ArchivoMovimientoRepositorio repo = new ArchivoMovimientoRepositorio(archivo);

        repo.registrar(new Movimiento(LocalDateTime.of(2026, 9, 25, 11, 0), "A1", TipoMovimiento.SALIDA, 2, 3, ""));

        assertEquals(2, new ArchivoMovimientoRepositorio(archivo).listar().size());
    }

    @Test
    void lineaIncompletaImpideCargar() throws IOException {
        // Comportamiento actual; la Fase 2 lo cambiará para saltar la línea con un aviso.
        Path archivo = carpeta.resolve("productos.csv");
        Files.writeString(archivo, "codigo;nombre;categoria;precio;stock;stockMinimo\nA1;Arroz\n");

        assertThrows(IllegalStateException.class, () -> new ArchivoProductoRepositorio(archivo));
    }
}
