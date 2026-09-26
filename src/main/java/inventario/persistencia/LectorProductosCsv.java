package inventario.persistencia;

import inventario.modelo.Producto;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lee un catálogo de productos desde CSV (separador ';'). La primera fila indica las columnas, en cualquier
 * orden: {@code codigo;nombre;precio} son obligatorias y {@code categoria;costo;stock;stockMinimo} opcionales.
 * Acepta el mismo formato que genera la exportación de inventario.
 */
public final class LectorProductosCsv {

    public record Resultado(List<Producto> productos, List<String> advertencias) {
    }

    private LectorProductosCsv() {
    }

    public static Resultado leer(Path archivo) {
        List<String> lineas = Archivos.leerLineas(archivo);
        if (lineas.isEmpty()) {
            return new Resultado(List.of(), List.of("El archivo está vacío."));
        }
        Map<String, Integer> columnas = new HashMap<>();
        List<String> cabecera = Csv.separar(lineas.getFirst().replace("﻿", ""));
        for (int i = 0; i < cabecera.size(); i++) {
            columnas.put(cabecera.get(i).strip().toLowerCase(Locale.ROOT), i);
        }
        for (String obligatoria : List.of("codigo", "nombre", "precio")) {
            if (!columnas.containsKey(obligatoria.toLowerCase(Locale.ROOT))) {
                return new Resultado(List.of(), List.of("Falta la columna \"" + obligatoria + "\" en la cabecera."));
            }
        }

        List<Producto> productos = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();
        Archivos.leerRegistros(archivo, 1, c -> productos.add(new Producto(
                campo(c, columnas, "codigo", ""), campo(c, columnas, "nombre", ""),
                campo(c, columnas, "categoria", ""),
                decimal(campo(c, columnas, "precio", "")), decimal(campo(c, columnas, "costo", "0")),
                Integer.parseInt(campo(c, columnas, "stock", "0")),
                Integer.parseInt(campo(c, columnas, "stockminimo", "0")))), advertencias);
        return new Resultado(productos, advertencias);
    }

    private static String campo(List<String> campos, Map<String, Integer> columnas, String nombre, String omision) {
        Integer i = columnas.get(nombre);
        if (i == null || i >= campos.size() || campos.get(i).isBlank()) {
            return omision;
        }
        return campos.get(i).strip();
    }

    private static BigDecimal decimal(String texto) {
        return new BigDecimal(texto.replace(',', '.'));
    }
}
