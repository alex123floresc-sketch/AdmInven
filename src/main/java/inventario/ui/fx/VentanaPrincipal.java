package inventario.ui.fx;

import inventario.servicio.InventarioServicio;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

/** Ventana principal: encabezado y pestañas de trabajo. */
final class VentanaPrincipal {

    private final Stage stage;
    private final List<Seccion> secciones;
    private final TabPane pestanas = new TabPane();

    VentanaPrincipal(Stage stage, InventarioServicio servicio, String ubicacionDatos) {
        this.stage = stage;
        secciones = List.of(
                new PestanaResumen(servicio),
                new PestanaProductos(servicio, this::refrescarTodo),
                new PestanaMovimientos(servicio));

        for (Seccion seccion : secciones) {
            Tab tab = new Tab(seccion.titulo(), seccion.vista());
            tab.setClosable(false);
            tab.setOnSelectionChanged(e -> {
                if (tab.isSelected()) {
                    seccion.refrescar();
                }
            });
            pestanas.getTabs().add(tab);
        }

        BorderPane raiz = new BorderPane();
        raiz.setTop(encabezado(ubicacionDatos));
        raiz.setCenter(pestanas);
        raiz.getStyleClass().add("raiz");

        Scene escena = new Scene(raiz, 1180, 720);
        escena.getStylesheets().add(AppFx.ESTILOS);
        stage.setScene(escena);
        stage.setTitle("Administrador de Inventario");
        stage.setMinWidth(900);
        stage.setMinHeight(560);
    }

    void mostrar() {
        refrescarTodo();
        stage.show();
    }

    /** Tras cualquier cambio se recargan todas las pestañas: los datos son pocos y así nada queda desfasado. */
    void refrescarTodo() {
        secciones.forEach(Seccion::refrescar);
    }

    private static Region encabezado(String ubicacionDatos) {
        Label titulo = new Label("Administrador de Inventario");
        titulo.getStyleClass().add("encabezado-titulo");
        Label subtitulo = new Label("Datos: " + ubicacionDatos);
        subtitulo.getStyleClass().add("encabezado-subtitulo");
        VBox textos = new VBox(2, titulo, subtitulo);
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox encabezado = new HBox(textos, espacio);
        encabezado.setAlignment(Pos.CENTER_LEFT);
        encabezado.setPadding(new Insets(14, 20, 14, 20));
        encabezado.getStyleClass().add("encabezado");
        return encabezado;
    }
}
