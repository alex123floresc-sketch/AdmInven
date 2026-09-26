package inventario.persistencia;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilidades mínimas para leer y escribir líneas CSV separadas por ';'.
 * Los campos con separador o comillas se encierran entre comillas dobles.
 */
final class Csv {

    static final char SEPARADOR = ';';

    private Csv() {
    }

    static String unir(Object... campos) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < campos.length; i++) {
            if (i > 0) {
                sb.append(SEPARADOR);
            }
            sb.append(escapar(String.valueOf(campos[i])));
        }
        return sb.toString();
    }

    static String escapar(String valor) {
        String limpio = valor.replace('\r', ' ').replace('\n', ' ');
        if (limpio.indexOf(SEPARADOR) >= 0 || limpio.indexOf('"') >= 0) {
            return '"' + limpio.replace("\"", "\"\"") + '"';
        }
        return limpio;
    }

    static List<String> separar(String linea) {
        List<String> campos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean entreComillas = false;
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (entreComillas) {
                if (c == '"' && i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                    actual.append('"');
                    i++;
                } else if (c == '"') {
                    entreComillas = false;
                } else {
                    actual.append(c);
                }
            } else if (c == '"') {
                entreComillas = true;
            } else if (c == SEPARADOR) {
                campos.add(actual.toString());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }
        campos.add(actual.toString());
        return campos;
    }
}
