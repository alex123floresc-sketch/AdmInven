package inventario.ui.fx;

import inventario.Aplicacion;
import inventario.Opciones;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.Objects;

/** Aplicación JavaFX. Se lanza desde {@link inventario.Main} con los mismos argumentos ({@link Opciones}). */
public class AppFx extends Application {

    static final String ESTILOS =
            Objects.requireNonNull(AppFx.class.getResource("estilos.css"), "falta estilos.css").toExternalForm();

    private Aplicacion aplicacion;

    @Override
    public void start(Stage stage) {
        Opciones opciones = Opciones.de(getParameters().getRaw());
        Path carpeta = opciones.carpetaDatos();
        try {
            aplicacion = opciones.iniciarAplicacion();
        } catch (RuntimeException e) {
            Dialogos.error(null, "No se pudo abrir la base de datos:\n" + e.getMessage());
            Platform.exit();
            return;
        }
        Thread.currentThread().setUncaughtExceptionHandler((hilo, error) ->
                Dialogos.error(stage, "Error inesperado: " + error.getMessage()));

        new VentanaPrincipal(stage, aplicacion, carpeta.toAbsolutePath().normalize().toString())
                .mostrar();
        if (!aplicacion.avisos().isEmpty()) {
            Dialogos.informacion(stage, "Avisos al iniciar", String.join("\n", aplicacion.avisos()));
        }
    }

    @Override
    public void stop() {
        if (aplicacion != null) {
            aplicacion.close();
        }
    }
}
