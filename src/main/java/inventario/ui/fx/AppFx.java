package inventario.ui.fx;

import inventario.Aplicacion;
import inventario.Opciones;
import inventario.modelo.Usuario;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.Objects;
import java.util.Optional;

/** Aplicación JavaFX. Se lanza desde {@link inventario.Main} con los mismos argumentos ({@link Opciones}). */
public class AppFx extends Application {

    static final String ESTILOS =
            Objects.requireNonNull(AppFx.class.getResource("estilos.css"), "falta estilos.css").toExternalForm();

    private Aplicacion aplicacion;
    private String ubicacionDatos;

    @Override
    public void start(Stage stage) {
        Opciones opciones = Opciones.de(getParameters().getRaw());
        ubicacionDatos = opciones.carpetaDatos().toAbsolutePath().normalize().toString();
        try {
            aplicacion = opciones.iniciarAplicacion();
        } catch (RuntimeException e) {
            Dialogos.error(null, "No se pudo abrir la base de datos:\n" + e.getMessage());
            Platform.exit();
            return;
        }
        Thread.currentThread().setUncaughtExceptionHandler((hilo, error) ->
                Dialogos.error(stage, "Error inesperado: " + error.getMessage()));
        // Al cerrar sesión la ventana se oculta un momento: la aplicación termina solo si el usuario la cierra.
        Platform.setImplicitExit(false);
        stage.setOnCloseRequest(e -> Platform.exit());

        if (!aplicacion.avisos().isEmpty()) {
            Dialogos.informacion(null, "Avisos al iniciar", String.join("\n", aplicacion.avisos()));
        }
        acceder(stage);
    }

    /** Pide identificarse y abre la ventana principal; al cerrar sesión se vuelve aquí. */
    private void acceder(Stage stage) {
        stage.hide();
        Optional<Usuario> usuario = aplicacion.usuarios().requiereConfiguracionInicial()
                ? AccesoDialogo.configuracionInicial(null, aplicacion.usuarios())
                : AccesoDialogo.iniciarSesion(null, aplicacion.usuarios(), aplicacion.esDemo());
        if (usuario.isEmpty()) {
            Platform.exit();
            return;
        }
        new VentanaPrincipal(stage, aplicacion, ubicacionDatos, () -> acceder(stage)).mostrar();
    }

    @Override
    public void stop() {
        if (aplicacion != null) {
            aplicacion.close();
        }
    }
}
