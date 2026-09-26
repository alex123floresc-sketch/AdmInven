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
import java.util.List;

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
