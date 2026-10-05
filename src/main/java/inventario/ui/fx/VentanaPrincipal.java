package inventario.ui.fx;

import inventario.Aplicacion;
import inventario.modelo.Permiso;
import inventario.modelo.Usuario;
import inventario.servicio.InventarioException;
import inventario.servicio.Sesion;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/** Ventana principal: encabezado con el usuario y las pestañas que su rol permite. */
final class VentanaPrincipal {

    private final Stage stage;
    private final Aplicacion app;
    private final Runnable alCerrarSesion;
    private final List<Seccion> secciones = new ArrayList<>();
    private final TabPane pestanas = new TabPane();

    VentanaPrincipal(Stage stage, Aplicacion app, String ubicacionDatos, Runnable alCerrarSesion) {
        this.stage = stage;
        this.app = app;
        this.alCerrarSesion = alCerrarSesion;
        Sesion sesion = app.sesion();

        secciones.add(new PestanaResumen(app.inventario(), app.reportes(), sesion));
        if (sesion.puede(Permiso.REGISTRAR_VENTAS)) {
            secciones.add(new PestanaVenta(app.inventario(), this::refrescarTodo));
        }
        secciones.add(new PestanaProductos(app, this::refrescarTodo));
        secciones.add(new PestanaMovimientos(app.inventario()));
        if (sesion.puede(Permiso.GESTIONAR_PROVEEDORES)) {
            secciones.add(new PestanaProveedores(app.proveedores(), this::refrescarTodo));
        }
        if (sesion.puede(Permiso.VER_REPORTES)) {
            secciones.add(new PestanaReportes(app.reportes()));
        }
        if (sesion.puede(Permiso.GESTIONAR_USUARIOS)) {
            secciones.add(new PestanaUsuarios(app.usuarios(), app.respaldos()));
        }

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
        raiz.setTop(encabezado(ubicacionDatos, sesion.usuario().orElseThrow()));
        raiz.setCenter(pestanas);
        raiz.getStyleClass().add("raiz");

        AppFx.mostrarEn(stage, raiz);
    }

    void mostrar() {
        refrescarTodo();
        stage.show();
    }

    /** Tras cualquier cambio se recargan todas las pestañas: los datos son pocos y así nada queda desfasado. */
    void refrescarTodo() {
        secciones.forEach(Seccion::refrescar);
    }

    private Region encabezado(String ubicacionDatos, Usuario usuario) {
        Label titulo = new Label("Administrador de Inventario");
        titulo.getStyleClass().add("encabezado-titulo");
        Label subtitulo = new Label("Datos: " + ubicacionDatos);
        subtitulo.getStyleClass().add("encabezado-subtitulo");
        VBox textos = new VBox(2, titulo, subtitulo);

        Label nombre = new Label(usuario.nombreCompleto());
        nombre.getStyleClass().add("encabezado-usuario");
        Label rol = new Label(usuario.rol().toString());
        rol.getStyleClass().add("encabezado-subtitulo");
        VBox quien = new VBox(2, nombre, rol);
        quien.setAlignment(Pos.CENTER_RIGHT);
        Button contrasena = new Button("Cambiar contraseña");
        contrasena.getStyleClass().add("boton-encabezado");
        contrasena.setOnAction(e -> cambiarContrasena());
        Button salir = new Button("Cerrar sesión");
        salir.getStyleClass().add("boton-encabezado");
        salir.setOnAction(e -> {
            app.usuarios().cerrarSesion();
            alCerrarSesion.run();
        });

        // Si falta espacio se acorta la ruta de los datos, nunca el usuario ni sus botones.
        textos.setMinWidth(0);
        HBox.setHgrow(textos, Priority.ALWAYS);
        for (Region fijo : List.of(quien, contrasena, salir)) {
            fijo.setMinWidth(Region.USE_PREF_SIZE);
        }
        HBox encabezado = new HBox(12, textos, quien, contrasena, salir);
        encabezado.setAlignment(Pos.CENTER_LEFT);
        encabezado.setPadding(new Insets(14, 20, 14, 20));
        encabezado.getStyleClass().add("encabezado");
        return encabezado;
    }

    private void cambiarContrasena() {
        PasswordField actual = new PasswordField();
        PasswordField nueva = new PasswordField();
        nueva.setPromptText("Mínimo 8, letras y números");
        PasswordField repetir = new PasswordField();
        GridPane rejilla = Dialogos.rejilla();
        Dialogos.fila(rejilla, "Contraseña actual", actual);
        Dialogos.fila(rejilla, "Nueva contraseña", nueva);
        Dialogos.fila(rejilla, "Repetir nueva", repetir);
        Platform.runLater(actual::requestFocus);
        Dialogos.formulario(stage, "Cambiar contraseña", "Guardar", rejilla, () -> {
            if (!nueva.getText().equals(repetir.getText())) {
                throw new InventarioException("Las contraseñas nuevas no coinciden.");
            }
            app.usuarios().cambiarMiContrasena(actual.getText().toCharArray(), nueva.getText().toCharArray());
            return Boolean.TRUE;
        }).ifPresent(x -> Dialogos.informacion(stage, "Contraseña cambiada", "Su contraseña se actualizó."));
    }
}
