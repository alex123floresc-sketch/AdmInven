package inventario.persistencia;

import inventario.modelo.Producto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportarExportarProductosTest {

    @TempDir
    Path carpeta;

    @Test
    void leeColumnasEnCualquierOrdenYConValoresPorOmision() throws IOException {
        Path archivo = carpeta.resolve("catalogo.csv");
        Files.writeString(archivo, """
                nombre;precio;codigo;stock
                Arroz;5,50;arr-01;12
                Sal;1.80;SAL-01;
                """);

        LectorProductosCsv.Resultado r = LectorProductosCsv.leer(archivo);

        assertTrue(r.advertencias().isEmpty());
        assertEquals(2, r.productos().size());
        Producto arroz = r.productos().getFirst();
        assertEquals("ARR-01", arroz.getCodigo());
        assertEquals(new BigDecimal("5.50"), arroz.getPrecio());
        assertEquals(12, arroz.getStock());
        assertEquals("General", arroz.getCategoria());
        assertEquals(0, r.productos().get(1).getStock());
    }

    @Test
    void faltaColumnaObligatoria() throws IOException {
        Path archivo = carpeta.resolve("catalogo.csv");
        Files.writeString(archivo, "codigo;nombre\nA1;Arroz\n");

        LectorProductosCsv.Resultado r = LectorProductosCsv.leer(archivo);

        assertTrue(r.productos().isEmpty());
        assertEquals(List.of("Falta la columna \"precio\" en la cabecera."), r.advertencias());
    }

    @Test
    void filasInvalidasSeInformanYElRestoSeLee() throws IOException {
        Path archivo = carpeta.resolve("catalogo.csv");
        Files.writeString(archivo, "codigo;nombre;precio\nA1;Arroz;caro\nB2;Sal;2\n;SinCodigo;1\n");

        LectorProductosCsv.Resultado r = LectorProductosCsv.leer(archivo);

        assertEquals(1, r.productos().size());
        assertEquals(2, r.advertencias().size());
    }

    @Test
    void loExportadoSePuedeVolverAImportar() {
        Path archivo = carpeta.resolve("inventario.csv");
        ExportadorCsv.escribir(archivo,
                List.of("codigo", "nombre", "categoria", "precio", "costo", "stock", "stockMinimo"),
                List.of(List.of("A1", "Arroz; 5 kg", "Abarrotes", "21.90", "17.50", "40", "10")));

        LectorProductosCsv.Resultado r = LectorProductosCsv.leer(archivo);

        Producto p = r.productos().getFirst();
        assertEquals("Arroz; 5 kg", p.getNombre());
        assertEquals(new BigDecimal("17.50"), p.getCosto());
        assertEquals(10, p.getStockMinimo());
    }
}
