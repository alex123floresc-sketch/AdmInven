package inventario;

import java.nio.file.Path;
import java.util.List;

/**
 * Argumentos de la línea de comandos: {@code [--consola] [--demo] [carpeta-de-datos]}.
 * Sin carpeta se usa {@code data}, o {@code demo} si se pidieron datos de ejemplo.
 */
public record Opciones(boolean consola, boolean demo, Path carpetaDatos) {

    public static Opciones de(List<String> argumentos) {
        boolean consola = argumentos.contains("--consola");
        boolean demo = argumentos.contains("--demo");
        Path carpeta = argumentos.stream().filter(a -> !a.startsWith("--")).findFirst()
                .map(Path::of).orElse(Path.of(demo ? "demo" : "data"));
        return new Opciones(consola, demo, carpeta);
    }

    public Aplicacion iniciarAplicacion() {
        return demo ? Aplicacion.iniciarDemo(carpetaDatos) : Aplicacion.iniciar(carpetaDatos);
    }
}
