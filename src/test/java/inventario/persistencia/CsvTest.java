package inventario.persistencia;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvTest {

    @Test
    void unirCamposSimples() {
        assertEquals("A1;Arroz;4.50;10", Csv.unir("A1", "Arroz", "4.50", 10));
    }

    @Test
    void campoConSeparadorOComillasSeEncierraEntreComillas() {
        assertEquals("\"a;b\"", Csv.escapar("a;b"));
        assertEquals("\"14\"\" pulgadas\"", Csv.escapar("14\" pulgadas"));
    }

    @Test
    void saltosDeLineaSeReemplazanPorEspacios() {
        assertEquals("linea 1  linea 2", Csv.escapar("linea 1\r\nlinea 2"));
    }

    @Test
    void separarLineaSimple() {
        assertEquals(List.of("A1", "Arroz", "4.50"), Csv.separar("A1;Arroz;4.50"));
    }

    @Test
    void separarConservaCamposVacios() {
        assertEquals(List.of("", "b", "", ""), Csv.separar(";b;;"));
    }

    @Test
    void idaYVueltaConservaTextoDificil() {
        List<String> original = List.of("Laptop; 14\"", "\"entre comillas\"", "", "ñandú áéíóú", ";;");

        List<String> recuperado = Csv.separar(Csv.unir(original.toArray()));

        assertEquals(original, recuperado);
    }
}
