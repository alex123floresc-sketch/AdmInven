package inventario.persistencia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
}
