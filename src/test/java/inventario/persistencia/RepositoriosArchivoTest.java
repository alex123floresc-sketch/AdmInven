package inventario.persistencia;

import inventario.modelo.Movimiento;
import inventario.modelo.Producto;
import inventario.modelo.TipoMovimiento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
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
    void estadoActivoSePersiste() {
        Path archivo = carpeta.resolve("productos.csv");
        ArchivoProductoRepositorio repo = new ArchivoProductoRepositorio(archivo);
        Producto baja = new Producto("A1", "Arroz", "", BigDecimal.ONE, 1, 0);
        baja.setActivo(false);
        repo.guardar(baja);
        repo.guardar(new Producto("B2", "Sal", "", BigDecimal.ONE, 1, 0));

        ArchivoProductoRepositorio recargado = new ArchivoProductoRepositorio(archivo);

        assertFalse(recargado.buscarPorCodigo("A1").orElseThrow().isActivo());
        assertTrue(recargado.buscarPorCodigo("B2").orElseThrow().isActivo());
    }

    @Test
    void archivoSinColumnaActivoCargaLosProductosComoActivos() throws IOException {
        Path archivo = carpeta.resolve("productos.csv");
        Files.writeString(archivo, "codigo;nombre;categoria;precio;stock;stockMinimo\nA1;Arroz;General;4.50;10;2\n");

        assertTrue(new ArchivoProductoRepositorio(archivo).buscarPorCodigo("A1").orElseThrow().isActivo());
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
    void siNoSePuedeEscribirElArchivoLaMemoriaQuedaComoEstaba() throws IOException {
        Path archivo = carpeta.resolve("productos.csv");
        ArchivoProductoRepositorio repo = new ArchivoProductoRepositorio(archivo);
        Producto original = new Producto("A1", "Arroz", "", BigDecimal.ONE, 1, 0);
        repo.guardar(original);
        // Una carpeta con contenido en lugar del archivo hace fallar la escritura.
        Files.delete(archivo);
        Files.createDirectories(archivo.resolve("bloqueo"));

        assertThrows(UncheckedIOException.class,
                () -> repo.guardar(new Producto("A1", "Cambiado", "", BigDecimal.TEN, 9, 0)));
        assertThrows(UncheckedIOException.class,
                () -> repo.guardar(new Producto("B2", "Nuevo", "", BigDecimal.ONE, 1, 0)));
        assertThrows(UncheckedIOException.class, () -> repo.eliminar("A1"));

        assertEquals(List.of(original), repo.listar());
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
    void lineasDanadasDeProductosSeIgnoranConAvisoYSeRespaldaElOriginal() throws IOException {
        Path archivo = carpeta.resolve("productos.csv");
        String original = "codigo;nombre;categoria;precio;stock;stockMinimo;activo\n"
                + "A1;Arroz\n"
                + "B2;Sal;General;barato;1;0;true\n"
                + "C3;Azúcar;General;3.00;-4;0;true\n"
                + "D4;Aceite;General;9.90;6;1;true\n";
        Files.writeString(archivo, original);

        ArchivoProductoRepositorio repo = new ArchivoProductoRepositorio(archivo);

        assertEquals(List.of("D4"), repo.listar().stream().map(Producto::getCodigo).toList());
        assertEquals(List.of(
                "Línea 2 de productos.csv ignorada: faltan columnas",
                "Línea 3 de productos.csv ignorada: número inválido",
                "Línea 4 de productos.csv ignorada: El stock no puede ser negativo.",
                "Se guardó una copia del archivo original en " + carpeta.resolve("productos.csv.respaldo") + "."),
                repo.getAdvertencias());
        assertEquals(original, Files.readString(carpeta.resolve("productos.csv.respaldo")));
    }

    @Test
    void archivoSinErroresNoGeneraAvisosNiRespaldo() {
        Path archivo = carpeta.resolve("productos.csv");
        new ArchivoProductoRepositorio(archivo).guardar(new Producto("A1", "Arroz", "", BigDecimal.ONE, 1, 0));

        assertTrue(new ArchivoProductoRepositorio(archivo).getAdvertencias().isEmpty());
        assertFalse(Files.exists(carpeta.resolve("productos.csv.respaldo")));
    }

    @Test
    void lineasDanadasDeMovimientosSeIgnoranConAviso() throws IOException {
        Path archivo = carpeta.resolve("movimientos.csv");
        Files.writeString(archivo, "fecha;codigoProducto;tipo;cantidad;stockResultante;nota\n"
                + "ayer;A1;ENTRADA;5;5;\n"
                + "2026-09-25T10:00;A1;ROBO;5;5;\n"
                + "2026-09-25T11:00;A1;SALIDA;2;3;Venta\n");

        ArchivoMovimientoRepositorio repo = new ArchivoMovimientoRepositorio(archivo);

        assertEquals(1, repo.listar().size());
        assertEquals(List.of(
                "Línea 2 de movimientos.csv ignorada: fecha inválida",
                "Línea 3 de movimientos.csv ignorada: tipo de movimiento desconocido \"ROBO\""),
                repo.getAdvertencias());
    }
}
