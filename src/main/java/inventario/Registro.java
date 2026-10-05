package inventario;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Registro de actividad en {@code registro0.log}, dentro de la carpeta de datos: arranques, inicios de sesión,
 * copias de seguridad y errores inesperados con su detalle, para diagnosticar problemas en el uso real.
 * <p>
 * Usa {@code java.util.logging} (incluido en Java): cada clase escribe con
 * {@code Logger.getLogger(Clase.class.getName())} y todo cuelga del registrador "inventario". Al llegar a 1 MB el archivo pasa a {@code registro1.log}
 * (y así hasta {@code registro2.log}, el más antiguo); nunca se vacía al arrancar, así que el error de la
 * ejecución anterior sigue ahí.
 */
public final class Registro {

    private static final Logger RAIZ = Logger.getLogger("inventario");
    private static final int TAMANO_MAXIMO = 1024 * 1024;
    private static final int ARCHIVOS = 3;
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Registro() {
    }

    /**
     * Empieza a escribir el registro en la carpeta de datos. Con {@code tambienEnConsola} los mensajes siguen
     * saliendo además por la salida de errores (útil en el servidor web); si no, solo van al archivo, para no
     * mezclarse con el menú de la consola.
     */
    public static void configurar(Path carpetaDatos, boolean tambienEnConsola) {
        RAIZ.setUseParentHandlers(tambienEnConsola);
        try {
            Files.createDirectories(carpetaDatos);
            // En el patrón de FileHandler "%" es especial: se duplica el de la ruta y "%g" numera los archivos.
            String patron = carpetaDatos.toAbsolutePath().toString().replace("%", "%%") + "/registro%g.log";
            FileHandler archivo = new FileHandler(patron, TAMANO_MAXIMO, ARCHIVOS, true);
            archivo.setEncoding(StandardCharsets.UTF_8.name());
            archivo.setFormatter(new FormatoLinea());
            RAIZ.addHandler(archivo);
        } catch (IOException | RuntimeException e) {
            // Sin registro la aplicación funciona igual: solo se avisa.
            RAIZ.setUseParentHandlers(true);
            RAIZ.log(Level.WARNING, "No se pudo crear el registro de actividad: " + e.getMessage());
        }
    }

    /** Deja de escribir en el archivo (lo usan las pruebas para liberar la carpeta temporal). */
    static void cerrar() {
        for (Handler h : RAIZ.getHandlers()) {
            if (h instanceof FileHandler) {
                RAIZ.removeHandler(h);
                h.close();
            }
        }
        RAIZ.setUseParentHandlers(true);
    }

    /** Una línea por mensaje: fecha, nivel, clase y texto; si hay una excepción, su traza completa debajo. */
    private static final class FormatoLinea extends Formatter {

        @Override
        public String format(LogRecord r) {
            String clase = r.getLoggerName() == null ? "" : r.getLoggerName().replaceFirst("^inventario\\.", "");
            StringBuilder linea = new StringBuilder()
                    .append(LocalDateTime.ofInstant(r.getInstant(), ZoneId.systemDefault()).format(FORMATO_FECHA))
                    .append(' ').append(nivel(r.getLevel()))
                    .append(" [").append(clase).append("] ")
                    .append(formatMessage(r))
                    .append(System.lineSeparator());
            if (r.getThrown() != null) {
                StringWriter traza = new StringWriter();
                r.getThrown().printStackTrace(new PrintWriter(traza));
                linea.append(traza);
            }
            return linea.toString();
        }

        private static String nivel(Level nivel) {
            if (nivel.intValue() >= Level.SEVERE.intValue()) {
                return "ERROR";
            }
            if (nivel.intValue() >= Level.WARNING.intValue()) {
                return "AVISO";
            }
            return "INFO ";
        }
    }
}
