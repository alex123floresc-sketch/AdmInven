package inventario.ui.fx;

import inventario.modelo.Rol;
import inventario.modelo.Usuario;
import inventario.servicio.InventarioException;
import inventario.servicio.RespaldoServicio;
import inventario.servicio.UsuarioServicio;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;

/** Administración de usuarios y copias de seguridad (solo administradores). */
final class PestanaUsuarios implements Seccion {

    private static final PseudoClass INACTIVO = PseudoClass.getPseudoClass("inactivo");

    private final UsuarioServicio servicio;
    private final RespaldoServicio respaldos;
    private final TableView<Usuario> tabla = Tablas.nueva("No hay usuarios.");
    private final Button activarODesactivar = new Button("Desactivar");
    private final BorderPane vista = new BorderPane();

    PestanaUsuarios(UsuarioServicio servicio, RespaldoServicio respaldos) {
        this.servicio = servicio;
        this.respaldos = respaldos;
        tabla.getColumns().add(Tablas.texto("Usuario", Usuario::nombreUsuario, 140));
        tabla.getColumns().add(Tablas.texto("Nombre", Usuario::nombreCompleto, 260));
        tabla.getColumns().add(Tablas.texto("Rol", u -> u.rol().toString(), 140));
        tabla.getColumns().add(Tablas.texto("Estado", u -> u.activo() ? "Activo" : "Desactivado", 120));
        tabla.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(Usuario u, boolean vacio) {
                super.updateItem(u, vacio);
                pseudoClassStateChanged(INACTIVO, !vacio && u != null && !u.activo());
            }
        });
        tabla.getSelectionModel().selectedItemProperty().addListener((o, a, u) ->
                activarODesactivar.setText(u != null && !u.activo() ? "Activar" : "Desactivar"));

        Button nuevo = new Button("Nuevo usuario");
        nuevo.getStyleClass().add("primario");
        nuevo.setOnAction(e -> nuevoUsuario());
        Button rol = new Button("Cambiar rol");
        rol.setOnAction(e -> cambiarRol());
        Button restablecer = new Button("Restablecer contraseña");
        restablecer.setOnAction(e -> restablecerContrasena());
        activarODesactivar.getStyleClass().add("peligro");
        activarODesactivar.setOnAction(e -> activarODesactivar());
        BooleanBinding sinSeleccion = tabla.getSelectionModel().selectedItemProperty().isNull();
        rol.disableProperty().bind(sinSeleccion);
        restablecer.disableProperty().bind(sinSeleccion);
        activarODesactivar.disableProperty().bind(sinSeleccion);

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(8, nuevo, rol, restablecer, espacio, activarODesactivar);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.setPadding(new Insets(0, 0, 12, 0));
        Label nota = new Label("Administrador: acceso completo.  Vendedor: consulta productos y registra ventas.");
        nota.getStyleClass().add("texto-secundario");
        Region espacioPie = new Region();
        HBox.setHgrow(espacioPie, Priority.ALWAYS);
        Button copia = new Button("Crear copia de seguridad");
        copia.setOnAction(e -> crearCopia());
        HBox pie = new HBox(8, nota, espacioPie, copia);
        pie.setAlignment(Pos.CENTER_LEFT);
        pie.setPadding(new Insets(8, 0, 0, 0));
        vista.setTop(barra);
        vista.setCenter(tabla);
        vista.setBottom(pie);
        vista.setPadding(new Insets(16));
    }

    @Override
    public String titulo() {
        return "Usuarios";
    }

    @Override
    public Node vista() {
        return vista;
    }

    @Override
    public void refrescar() {
        Usuario seleccionado = tabla.getSelectionModel().getSelectedItem();
        tabla.getItems().setAll(servicio.listar());
        if (seleccionado != null) {
            tabla.getItems().stream().filter(u -> u.nombreUsuario().equals(seleccionado.nombreUsuario()))
                    .findFirst().ifPresent(u -> tabla.getSelectionModel().select(u));
        }
    }

    private void nuevoUsuario() {
        TextField usuario = new TextField();
        usuario.setPromptText("Ej. lquispe");
        TextField nombre = new TextField();
        ComboBox<Rol> rol = new ComboBox<>();
        rol.getItems().setAll(Rol.values());
        rol.setValue(Rol.VENDEDOR);
        PasswordField contrasena = new PasswordField();
        contrasena.setPromptText("Mínimo 8, letras y números");

        GridPane rejilla = Dialogos.rejilla();
        Dialogos.fila(rejilla, "Usuario", usuario);
        Dialogos.fila(rejilla, "Nombre completo", nombre);
        Dialogos.fila(rejilla, "Rol", rol);
        Dialogos.fila(rejilla, "Contraseña inicial", contrasena);
        Platform.runLater(usuario::requestFocus);

        Dialogos.formulario(ventana(), "Nuevo usuario", "Crear", rejilla,
                        () -> servicio.crearUsuario(usuario.getText(), nombre.getText(), rol.getValue(),
                                contrasena.getText().toCharArray()))
                .ifPresent(u -> refrescar());
    }

    private void cambiarRol() {
        Usuario u = tabla.getSelectionModel().getSelectedItem();
        if (u == null) {
            return;
        }
        ComboBox<Rol> rol = new ComboBox<>();
        rol.getItems().setAll(Rol.values());
        rol.setValue(u.rol());
        GridPane rejilla = Dialogos.rejilla();
        Dialogos.fila(rejilla, "Usuario", new Label(u.nombreCompleto()));
        Dialogos.fila(rejilla, "Rol", rol);
        Dialogos.formulario(ventana(), "Cambiar rol", "Guardar", rejilla, () -> {
            servicio.cambiarRol(u.nombreUsuario(), rol.getValue());
            return Boolean.TRUE;
        }).ifPresent(x -> refrescar());
    }

    private void restablecerContrasena() {
        Usuario u = tabla.getSelectionModel().getSelectedItem();
        if (u == null) {
            return;
        }
        PasswordField nueva = new PasswordField();
        nueva.setPromptText("Mínimo 8, letras y números");
        GridPane rejilla = Dialogos.rejilla();
        Dialogos.fila(rejilla, "Usuario", new Label(u.nombreCompleto()));
        Dialogos.fila(rejilla, "Nueva contraseña", nueva);
        Platform.runLater(nueva::requestFocus);
        Dialogos.formulario(ventana(), "Restablecer contraseña", "Guardar", rejilla, () -> {
            servicio.restablecerContrasena(u.nombreUsuario(), nueva.getText().toCharArray());
            return Boolean.TRUE;
        }).ifPresent(x -> Dialogos.informacion(ventana(), "Contraseña restablecida",
                "Comunique la nueva contraseña a " + u.nombreCompleto() + "."));
    }

    private void activarODesactivar() {
        Usuario u = tabla.getSelectionModel().getSelectedItem();
        if (u == null) {
            return;
        }
        try {
            if (u.activo()) {
                if (Dialogos.confirmar(ventana(), "Desactivar usuario", "¿Desactivar a " + u.nombreCompleto()
                        + "?\nNo podrá iniciar sesión; sus movimientos se conservan.", "Desactivar")) {
                    servicio.desactivar(u.nombreUsuario());
                }
            } else {
                servicio.activar(u.nombreUsuario());
            }
            refrescar();
        } catch (InventarioException e) {
            Dialogos.error(ventana(), e.getMessage());
        }
    }

    private void crearCopia() {
        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar copia de seguridad");
        selector.setInitialFileName(respaldos.nombreSugerido());
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Base de datos SQLite", "*.db"));
        File archivo = selector.showSaveDialog(ventana());
        if (archivo == null) {
            return;
        }
        try {
            respaldos.crearCopia(archivo.toPath());
            Dialogos.informacion(ventana(), "Copia de seguridad creada", "Todos los datos se guardaron en:\n"
                    + archivo + "\n\nPara restaurarla, cierre el programa y reemplace inventario.db de la "
                    + "carpeta de datos por esta copia (con el mismo nombre).");
        } catch (InventarioException e) {
            Dialogos.error(ventana(), e.getMessage());
        }
    }

    private Window ventana() {
        return vista.getScene().getWindow();
    }
}
