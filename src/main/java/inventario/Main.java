package inventario;

import inventario.ui.MenuConsola;
import inventario.ui.fx.AppFx;
import javafx.application.Application;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Scanner;

/**
 * Punto de entrada: {@code [--consola] [--demo] [carpeta-de-datos]} (ver {@link Opciones}).
 * Por defecto abre la ventana; con {@code --consola} usa el menú de texto.
 * <p>
 * No extiende {@link Application} a propósito: así el JAR con todas las dependencias puede arrancar JavaFX.
 */
public class Main {

    public static void main(String[] args) {
        Opciones opciones = Opciones.de(List.of(args));
        if (opciones.consola()) {
            ejecutarConsola(opciones);
        } else {
            Application.launch(AppFx.class, args);
        }
    }

    private static void ejecutarConsola(Opciones opciones) {
        try (Aplicacion app = opciones.iniciarAplicacion()) {
            app.avisos().forEach(a -> System.out.println("! " + a));
            // Usa la codificación real de la consola para leer bien tildes y eñes en Windows.
            Charset codificacion = Charset.forName(
                    System.getProperty("stdin.encoding", Charset.defaultCharset().name()));
            new MenuConsola(app.inventario(), new Scanner(System.in, codificacion), System.out).iniciar();
        }
    }
}
