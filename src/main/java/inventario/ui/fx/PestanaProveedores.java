package inventario.ui.fx;

import inventario.modelo.Proveedor;
import inventario.servicio.InventarioException;
import inventario.servicio.ProveedorServicio;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Window;

import java.util.Optional;

/** Registro de proveedores: alta, edición, baja y reactivación. */
final class PestanaProveedores implements Seccion {

    private static final PseudoClass INACTIVO = PseudoClass.getPseudoClass("inactivo");

    private final ProveedorServicio servicio;
    private final Runnable alCambiar;
    private final TableView<Proveedor> tabla = Tablas.nueva("Aún no hay proveedores. Registre el primero.");
    private final Button bajaOReactivar = new Button("Dar de baja");
    private final BorderPane vista = new BorderPane();

    PestanaProveedores(ProveedorServicio servicio, Runnable alCambiar) {
        this.servicio = servicio;
        this.alCambiar = alCambiar;

        tabla.getColumns().add(Tablas.texto("Nombre", Proveedor::nombre, 240));
        tabla.getColumns().add(Tablas.texto("RUC / DNI", Proveedor::documento, 120));
        tabla.getColumns().add(Tablas.texto("Teléfono", Proveedor::telefono, 120));
        tabla.getColumns().add(Tablas.texto("Correo", Proveedor::email, 220));
        tabla.getColumns().add(Tablas.texto("Estado", p -> p.activo() ? "Activo" : "De baja", 90));
        tabla.setRowFactory(t -> {
            TableRow<Proveedor> fila = new TableRow<>() {
                @Override
                protected void updateItem(Proveedor p, boolean vacio) {
                    super.updateItem(p, vacio);
                    pseudoClassStateChanged(INACTIVO, !vacio && p != null && !p.activo());
                }
            };
            fila.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !fila.isEmpty()) {
                    editar();
                }
            });
            return fila;
        });
        tabla.getSelectionModel().selectedItemProperty().addListener((o, a, p) ->
                bajaOReactivar.setText(p != null && !p.activo() ? "Reactivar" : "Dar de baja"));

        Button nuevo = new Button("Nuevo proveedor");
        nuevo.getStyleClass().add("primario");
        nuevo.setOnAction(e -> accion(() -> formulario(null).ifPresent(p -> cambio())));
        Button editar = new Button("Editar");
        editar.setOnAction(e -> editar());
        bajaOReactivar.getStyleClass().add("peligro");
        bajaOReactivar.setOnAction(e -> bajaOReactivar());
        BooleanBinding sinSeleccion = tabla.getSelectionModel().selectedItemProperty().isNull();
        editar.disableProperty().bind(sinSeleccion);
        bajaOReactivar.disableProperty().bind(sinSeleccion);

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(8, nuevo, editar, espacio, bajaOReactivar);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.setPadding(new Insets(0, 0, 12, 0));
        vista.setTop(barra);
        vista.setCenter(tabla);
        vista.setPadding(new Insets(16));
    }

    @Override
    public String titulo() {
        return "Proveedores";
    }

    @Override
    public Node vista() {
        return vista;
    }

    @Override
    public void refrescar() {
        Proveedor seleccionado = tabla.getSelectionModel().getSelectedItem();
        tabla.getItems().setAll(servicio.listarTodos());
        if (seleccionado != null) {
            tabla.getItems().stream().filter(p -> p.id().equals(seleccionado.id())).findFirst()
                    .ifPresent(p -> tabla.getSelectionModel().select(p));
        }
    }

    private void editar() {
        Proveedor p = tabla.getSelectionModel().getSelectedItem();
        if (p != null) {
            accion(() -> formulario(p).ifPresent(x -> cambio()));
        }
    }

    private void bajaOReactivar() {
        Proveedor p = tabla.getSelectionModel().getSelectedItem();
        if (p == null) {
            return;
        }
        accion(() -> {
            if (!p.activo()) {
                servicio.reactivar(p.id());
                cambio();
            } else if (Dialogos.confirmar(ventana(), "Dar de baja", "¿Dar de baja a \"" + p.nombre()
                    + "\"?\nNo aparecerá al registrar compras, pero sus compras anteriores se conservan.",
                    "Dar de baja")) {
                servicio.darDeBaja(p.id());
                cambio();
            }
        });
    }

    private Optional<Proveedor> formulario(Proveedor actual) {
        boolean nuevo = actual == null;
        TextField nombre = new TextField(nuevo ? "" : actual.nombre());
        TextField documento = new TextField(nuevo ? "" : actual.documento());
        documento.setPromptText("RUC (11 dígitos) o DNI (8)");
        TextField telefono = new TextField(nuevo ? "" : actual.telefono());
        TextField email = new TextField(nuevo ? "" : actual.email());
        email.setPromptText("ventas@empresa.com");

        GridPane rejilla = Dialogos.rejilla();
        Dialogos.fila(rejilla, "Nombre", nombre);
        Dialogos.fila(rejilla, "RUC / DNI", documento);
        Dialogos.fila(rejilla, "Teléfono", telefono);
        Dialogos.fila(rejilla, "Correo", email);
        Platform.runLater(nombre::requestFocus);

        return Dialogos.formulario(ventana(), nuevo ? "Nuevo proveedor" : "Editar proveedor", "Guardar", rejilla,
                () -> nuevo
                        ? servicio.registrar(nombre.getText(), documento.getText(), telefono.getText(), email.getText())
                        : servicio.actualizar(actual.id(), nombre.getText(), documento.getText(),
                        telefono.getText(), email.getText()));
    }

    private void cambio() {
        alCambiar.run();
    }

    private void accion(Runnable accion) {
        try {
            accion.run();
        } catch (InventarioException e) {
            Dialogos.error(ventana(), e.getMessage());
        }
    }

    private Window ventana() {
        return vista.getScene().getWindow();
    }
}
