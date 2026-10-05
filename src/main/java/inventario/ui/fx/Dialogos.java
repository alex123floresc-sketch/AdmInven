package inventario.ui.fx;

import inventario.servicio.InventarioException;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Diálogos comunes con el estilo de la aplicación. */
final class Dialogos {

    private static final Logger REGISTRO = Logger.getLogger(Dialogos.class.getName());

    static final ButtonType CANCELAR = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
    static final ButtonType CERRAR = new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE);

    private Dialogos() {
    }

    static void error(Window duenio, String mensaje) {
        mostrarAlerta(duenio, Alert.AlertType.ERROR, "Error", mensaje);
    }

    static void informacion(Window duenio, String titulo, String mensaje) {
        mostrarAlerta(duenio, Alert.AlertType.INFORMATION, titulo, mensaje);
    }

    static boolean confirmar(Window duenio, String titulo, String mensaje, String textoAceptar) {
        ButtonType aceptar = new ButtonType(textoAceptar, ButtonBar.ButtonData.OK_DONE);
        Alert alerta = new Alert(Alert.AlertType.CONFIRMATION, mensaje, aceptar, CANCELAR);
        preparar(alerta, duenio, titulo);
        return alerta.showAndWait().filter(aceptar::equals).isPresent();
    }

    /**
     * Muestra un formulario. Al pulsar el botón principal ejecuta {@code accion}; si lanza un error de negocio
     * el mensaje aparece dentro del diálogo y este sigue abierto para corregir los datos.
     */
    static <T> Optional<T> formulario(Window duenio, String titulo, String textoAceptar, Node contenido,
                                      Supplier<T> accion) {
        Dialog<T> dialogo = new Dialog<>();
        preparar(dialogo, duenio, titulo);
        ButtonType aceptar = new ButtonType(textoAceptar, ButtonBar.ButtonData.OK_DONE);
        DialogPane panel = dialogo.getDialogPane();
        panel.getButtonTypes().addAll(aceptar, CANCELAR);

        Label error = new Label();
        error.getStyleClass().add("mensaje-error");
        error.setWrapText(true);
        error.managedProperty().bind(error.textProperty().isNotEmpty());
        error.visibleProperty().bind(error.textProperty().isNotEmpty());
        VBox cuerpo = new VBox(14, contenido, error);
        cuerpo.setPrefWidth(440);
        panel.setContent(cuerpo);

        AtomicReference<T> resultado = new AtomicReference<>();
        Button boton = (Button) panel.lookupButton(aceptar);
        boton.getStyleClass().add("primario");
        boton.addEventFilter(ActionEvent.ACTION, evento -> {
            try {
                resultado.set(accion.get());
            } catch (InventarioException | IllegalArgumentException e) {
                error.setText(e.getMessage());
                evento.consume();
            } catch (RuntimeException e) {
                REGISTRO.log(Level.SEVERE, "Error inesperado en un formulario", e);
                error.setText("Error inesperado: " + e.getMessage());
                evento.consume();
            }
        });
        dialogo.setResultConverter(tipo -> tipo == aceptar ? resultado.get() : null);
        return dialogo.showAndWait();
    }

    /** Rejilla de dos columnas (etiqueta, campo) para formularios. */
    static GridPane rejilla() {
        GridPane rejilla = new GridPane();
        rejilla.setHgap(12);
        rejilla.setVgap(10);
        ColumnConstraints etiquetas = new ColumnConstraints();
        etiquetas.setMinWidth(120);
        ColumnConstraints campos = new ColumnConstraints();
        campos.setHgrow(Priority.ALWAYS);
        campos.setFillWidth(true);
        rejilla.getColumnConstraints().addAll(etiquetas, campos);
        return rejilla;
    }

    static void fila(GridPane rejilla, String etiqueta, Node campo) {
        int fila = rejilla.getRowCount();
        Label texto = new Label(etiqueta);
        texto.getStyleClass().add("etiqueta-campo");
        rejilla.addRow(fila, texto, campo);
        if (campo instanceof javafx.scene.control.Control control) {
            control.setMaxWidth(Double.MAX_VALUE);
        }
    }

    static void preparar(Dialog<?> dialogo, Window duenio, String titulo) {
        if (duenio != null) {
            dialogo.initOwner(duenio);
        }
        dialogo.setTitle(titulo);
        dialogo.setHeaderText(null);
        dialogo.setGraphic(null);
        dialogo.getDialogPane().getStylesheets().add(AppFx.ESTILOS);
    }

    private static void mostrarAlerta(Window duenio, Alert.AlertType tipo, String titulo, String mensaje) {
        Alert alerta = new Alert(tipo, mensaje, CERRAR);
        preparar(alerta, duenio, titulo);
        alerta.showAndWait();
    }
}
