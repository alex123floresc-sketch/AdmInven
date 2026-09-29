package inventario;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Argumentos de la línea de comandos: {@code [--consola | --web] [--puerto=N] [--demo] [carpeta-de-datos]}.
 * <p>
 * Sin carpeta se usa {@code data} (o {@code demo} con datos de ejemplo) junto al programa. La aplicación
 * instalada (creada con jpackage) no puede escribir en su carpeta de instalación, así que guarda los datos en
 * {@code AdministradorInventario} dentro de la carpeta del usuario.
 */
public record Opciones(boolean consola, boolean web, int puerto, boolean demo, Path carpetaDatos) {

    /** Puerto del servidor web si no se indica otro con {@code --puerto} o la variable de entorno PORT. */
    public static final int PUERTO_POR_OMISION = 7070;

    public static Opciones de(List<String> argumentos) {
        boolean consola = argumentos.contains("--consola");
        boolean web = argumentos.contains("--web");
        boolean demo = argumentos.contains("--demo");
        int puerto = argumentos.stream().filter(a -> a.startsWith("--puerto=")).findFirst()
                .map(a -> a.substring("--puerto=".length()))
                .or(() -> Optional.ofNullable(System.getenv("PORT")))
                .map(Opciones::leerPuerto).orElse(PUERTO_POR_OMISION);
        Path carpeta = argumentos.stream().filter(a -> !a.startsWith("--")).findFirst()
                .map(Path::of).orElseGet(() -> carpetaPorOmision(demo));
        return new Opciones(consola, web, puerto, demo, carpeta);
    }

    public Aplicacion iniciarAplicacion() {
        return demo ? Aplicacion.iniciarDemo(carpetaDatos) : Aplicacion.iniciar(carpetaDatos);
    }

    private static Path carpetaPorOmision(boolean demo) {
        String nombre = demo ? "demo" : "data";
        // El lanzador de jpackage define esta propiedad; así se sabe que es la aplicación instalada.
        if (System.getProperty("jpackage.app-version") != null) {
            return Path.of(System.getProperty("user.home"), "AdministradorInventario", nombre);
        }
        return Path.of(nombre);
    }

    private static int leerPuerto(String texto) {
        try {
            int puerto = Integer.parseInt(texto.strip());
            if (puerto < 1 || puerto > 65535) {
                throw new NumberFormatException();
            }
            return puerto;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Puerto no válido: " + texto);
        }
    }
}
