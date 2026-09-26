package inventario.ui.fx;

import inventario.servicio.InventarioException;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Conversión entre valores del dominio y texto en pantalla. */
final class Formatos {

    /** Símbolo de moneda mostrado en pantalla (soles peruanos). */
    static final String MONEDA = "S/";

    private static final DecimalFormat IMPORTE =
            new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.of("es", "PE")));
    private static final DecimalFormat ENTERO =
            new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.of("es", "PE")));
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Formatos() {
    }

    static String moneda(BigDecimal valor) {
        return MONEDA + " " + IMPORTE.format(valor);
    }

    static String numero(BigDecimal valor) {
        return IMPORTE.format(valor);
    }

    static String entero(Number valor) {
        return ENTERO.format(valor);
    }

    static String fechaHora(LocalDateTime fecha) {
        return fecha.format(FECHA_HORA);
    }

    static String fecha(LocalDate fecha) {
        return fecha.format(FECHA);
    }

    /** Acepta coma o punto decimal; el nombre del campo aparece en el mensaje de error. */
    static BigDecimal leerDecimal(String campo, String texto) {
        try {
            return new BigDecimal(texto.strip().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new InventarioException(campo + " debe ser un número, por ejemplo 12.50.");
        }
    }

    static int leerEntero(String campo, String texto) {
        try {
            return Integer.parseInt(texto.strip());
        } catch (NumberFormatException e) {
            throw new InventarioException(campo + " debe ser un número entero.");
        }
    }
}
