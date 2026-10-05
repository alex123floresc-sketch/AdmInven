package inventario;

import inventario.ui.MenuConsola;
import inventario.ui.fx.AppFx;
import inventario.ui.web.ServidorWeb;
import javafx.application.Application;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Punto de entrada: {@code [--consola | --web] [--puerto=N] [--demo] [carpeta-de-datos]} (ver {@link Opciones}).
 * Por defecto abre la ventana; con {@code --consola} usa el menú de texto y con {@code --web} inicia el servidor
 * para usar la aplicación desde el navegador.
 * <p>
 * No extiende {@link Application} a propósito: así el JAR con todas las dependencias puede arrancar JavaFX.
 */
public class Main {

    /**
     * Registro de JavaFX. Se guarda en un campo para que no se descarte su configuración: JavaFX avisa
     * "Unsupported JavaFX configuration" al cargarse desde el classpath (lo normal en un JAR ejecutable),
     * un aviso que no afecta en nada al funcionamiento.
     */
    private static final Logger REGISTRO_JAVAFX = Logger.getLogger("javafx");
    private static final Logger REGISTRO = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        Opciones opciones;
        try {
            opciones = Opciones.de(List.of(args));
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }
        Registro.configurar(opciones.carpetaDatos(), opciones.web());
        String modo = opciones.web() ? "web" : opciones.consola() ? "consola" : "ventana";
        REGISTRO.info(() -> "Inicio en modo " + modo + (opciones.demo() ? " (demostración)" : "")
                + ", datos en " + opciones.carpetaDatos().toAbsolutePath().normalize());
        if (opciones.web()) {
            ejecutarWeb(opciones);
        } else if (opciones.consola()) {
            ejecutarConsola(opciones);
        } else {
            REGISTRO_JAVAFX.setLevel(Level.SEVERE);
            Application.launch(AppFx.class, args);
        }
    }

    /** El servidor sigue atendiendo hasta que se detiene el proceso (Ctrl+C). */
    private static void ejecutarWeb(Opciones opciones) {
        Aplicacion app = opciones.iniciarAplicacion();
        ServidorWeb servidor = new ServidorWeb(app).iniciar(opciones.puerto());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            servidor.close();
            app.close();
        }));
        app.avisos().forEach(a -> System.out.println("! " + a));
        System.out.println("Administrador de Inventario disponible en http://localhost:" + servidor.puerto());
        System.out.println("Datos: " + opciones.carpetaDatos().toAbsolutePath().normalize());
        if (app.esDemo()) {
            System.out.println("Usuarios de demostración: " + Aplicacion.CREDENCIALES_DEMO);
        }
        System.out.println("Pulse Ctrl+C para detener el servidor.");
    }

    private static void ejecutarConsola(Opciones opciones) {
        try (Aplicacion app = opciones.iniciarAplicacion()) {
            app.avisos().forEach(a -> System.out.println("! " + a));
            // Usa la codificación real de la consola para leer bien tildes y eñes en Windows.
            Charset codificacion = Charset.forName(
                    System.getProperty("stdin.encoding", Charset.defaultCharset().name()));
            if (app.esDemo()) {
                System.out.println("Usuarios de demostración: " + Aplicacion.CREDENCIALES_DEMO);
            }
            new MenuConsola(app.inventario(), app.usuarios(), new Scanner(System.in, codificacion), System.out)
                    .iniciar();
        }
    }
}
