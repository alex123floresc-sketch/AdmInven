package inventario.persistencia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Consumer;

final class Archivos {

    private Archivos() {
    }

    static List<String> leerLineas(Path archivo) {
        if (!Files.exists(archivo)) {
            return List.of();
        }
        try {
            return Files.readAllLines(archivo, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + archivo, e);
        }
    }

    /**
     * Procesa cada línea de datos (se salta la cabecera y las líneas en blanco). Una línea que no se puede
     * interpretar no detiene la carga: se ignora y se describe en {@code advertencias}.
     */
    static void leerRegistros(Path archivo, int columnasMinimas, Consumer<List<String>> procesar,
                              List<String> advertencias) {
        List<String> lineas = leerLineas(archivo);
        for (int i = 1; i < lineas.size(); i++) {
            String linea = lineas.get(i);
            if (linea.isBlank()) {
                continue;
            }
            try {
                List<String> campos = Csv.separar(linea);
                if (campos.size() < columnasMinimas) {
                    throw new IllegalArgumentException("faltan columnas");
                }
                procesar.accept(campos);
            } catch (RuntimeException e) {
                advertencias.add("Línea " + (i + 1) + " de " + archivo.getFileName() + " ignorada: " + describir(e));
            }
        }
    }

    private static String describir(RuntimeException e) {
        return switch (e) {
            case NumberFormatException _ -> "número inválido";
            case DateTimeParseException _ -> "fecha inválida";
            default -> e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        };
    }

    /** Escribe en un archivo temporal y lo reemplaza, para no dejar el archivo a medias. */
    static void escribirLineas(Path archivo, List<String> lineas) {
        try {
            Path padre = archivo.toAbsolutePath().getParent();
            Files.createDirectories(padre);
            Path temporal = Files.createTempFile(padre, archivo.getFileName().toString(), ".tmp");
            Files.write(temporal, lineas, StandardCharsets.UTF_8);
            Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir " + archivo, e);
        }
    }

    /** Copia el archivo junto al original con extensión ".respaldo" y devuelve la ruta de la copia. */
    static Path respaldar(Path archivo) {
        Path copia = archivo.resolveSibling(archivo.getFileName() + ".respaldo");
        try {
            Files.copy(archivo, copia, StandardCopyOption.REPLACE_EXISTING);
            return copia;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo respaldar " + archivo, e);
        }
    }

    /** Añade una línea al final; si el archivo no existe o está vacío, escribe antes la cabecera. */
    static void agregarLinea(Path archivo, String cabecera, String linea) {
        try {
            Files.createDirectories(archivo.toAbsolutePath().getParent());
            StringBuilder texto = new StringBuilder();
            if (!Files.exists(archivo) || Files.size(archivo) == 0) {
                texto.append(cabecera).append(System.lineSeparator());
            } else if (!terminaEnSaltoDeLinea(archivo)) {
                texto.append(System.lineSeparator());
            }
            texto.append(linea).append(System.lineSeparator());
            Files.writeString(archivo, texto, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir " + archivo, e);
        }
    }

    private static boolean terminaEnSaltoDeLinea(Path archivo) throws IOException {
        try (SeekableByteChannel canal = Files.newByteChannel(archivo)) {
            canal.position(canal.size() - 1);
            ByteBuffer ultimo = ByteBuffer.allocate(1);
            canal.read(ultimo);
            return ultimo.get(0) == '\n';
        }
    }
}
