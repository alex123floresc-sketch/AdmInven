package inventario.persistencia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Escribe tablas en CSV que Excel abre directamente (UTF-8 con BOM y separador ';'). */
public final class ExportadorCsv {

    /** Marca que le indica a Excel que el archivo está en UTF-8 (si no, las tildes salen mal). */
    private static final String BOM = "﻿";

    private ExportadorCsv() {
    }

    public static void escribir(Path archivo, List<String> cabecera, List<List<String>> filas) {
        List<String> lineas = new ArrayList<>(filas.size() + 1);
        lineas.add(BOM + Csv.unir(cabecera.toArray()));
        for (List<String> fila : filas) {
            lineas.add(Csv.unir(fila.toArray()));
        }
        try {
            Files.write(archivo, lineas, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir " + archivo, e);
        }
    }
}
