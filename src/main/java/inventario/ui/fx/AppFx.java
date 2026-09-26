package inventario.ui.fx;

import inventario.Aplicacion;
import inventario.Opciones;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

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

        stage.setTitle("Administrador de Inventario");
        stage.setMinWidth(900);
        stage.setMinHeight(560);
        mostrarAcceso(stage);
        stage.show();
        if (!aplicacion.avisos().isEmpty()) {
            Dialogos.informacion(stage, "Avisos al iniciar", String.join("\n", aplicacion.avisos()));
        }
    }

    /** La misma ventana muestra el acceso y, tras identificarse, el trabajo; al cerrar sesión vuelve aquí. */
    private void mostrarAcceso(Stage stage) {
        PantallaAcceso acceso = new PantallaAcceso(aplicacion.usuarios(), aplicacion.esDemo(),
                usuario -> new VentanaPrincipal(stage, aplicacion, ubicacionDatos, () -> mostrarAcceso(stage))
                        .mostrar());
        mostrarEn(stage, acceso.vista());
    }

    /** Cambia el contenido de la ventana conservando su tamaño (o si está maximizada). */
    static void mostrarEn(Stage stage, Parent contenido) {
        if (stage.getScene() == null) {
            Scene escena = new Scene(contenido, 1180, 720);
            escena.getStylesheets().add(ESTILOS);
            stage.setScene(escena);
        } else {
            stage.getScene().setRoot(contenido);
        }
    }

    @Override
    public void stop() {
        if (aplicacion != null) {
            aplicacion.close();
        }
    }
}
