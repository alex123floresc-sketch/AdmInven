package inventario.ui.fx;

import inventario.Aplicacion;
import inventario.modelo.Usuario;
import inventario.servicio.InventarioException;
import inventario.servicio.UsuarioServicio;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Pantalla de inicio de sesión (o de creación del primer administrador) que ocupa la ventana principal.
 * Al ser parte de la ventana, nunca queda escondida detrás de otras aplicaciones.
 */
final class PantallaAcceso {

    private final UsuarioServicio usuarios;
    private final boolean demo;
    private final Consumer<Usuario> alEntrar;
    private final Label error = new Label();

    PantallaAcceso(UsuarioServicio usuarios, boolean demo, Consumer<Usuario> alEntrar) {
        this.usuarios = usuarios;
        this.demo = demo;
        this.alEntrar = alEntrar;
        error.getStyleClass().add("mensaje-error");
        error.setWrapText(true);
        error.managedProperty().bind(error.textProperty().isNotEmpty());
        error.visibleProperty().bind(error.textProperty().isNotEmpty());
    }

    Parent vista() {
        VBox tarjeta = usuarios.requiereConfiguracionInicial() ? formularioInicial() : formularioAcceso();
        tarjeta.getStyleClass().add("tarjeta-acceso");
        tarjeta.setMaxWidth(420);
        tarjeta.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane fondo = new StackPane(tarjeta);
        fondo.getStyleClass().add("fondo-acceso");
        fondo.setPadding(new Insets(24));
        return fondo;
    }

    private VBox formularioAcceso() {
        TextField usuario = new TextField();
        usuario.setPromptText("usuario");
        PasswordField contrasena = new PasswordField();
        contrasena.setPromptText("contraseña");
        Button entrar = botonPrincipal("Entrar", () -> {
            try {
                return usuarios.iniciarSesion(usuario.getText(), contrasena.getText().toCharArray());
            } finally {
                contrasena.clear();
            }
        });

        GridPane rejilla = Dialogos.rejilla();
        Dialogos.fila(rejilla, "Usuario", usuario);
        Dialogos.fila(rejilla, "Contraseña", contrasena);
        VBox tarjeta = new VBox(16, encabezado("Inicie sesión para continuar."), rejilla, error, entrar);
        if (demo) {
            Label ayuda = new Label("Usuarios de demostración:\n" + Aplicacion.CREDENCIALES_DEMO);
            ayuda.getStyleClass().add("aviso-demo");
            ayuda.setWrapText(true);
            ayuda.setMaxWidth(Double.MAX_VALUE);
            tarjeta.getChildren().add(ayuda);
        }
        Platform.runLater(usuario::requestFocus);
        return tarjeta;
    }

    private VBox formularioInicial() {
        TextField usuario = new TextField("admin");
        TextField nombre = new TextField();
        nombre.setPromptText("Ej. Ana Torres");
        PasswordField contrasena = new PasswordField();
        PasswordField repetir = new PasswordField();
        Button crear = botonPrincipal("Crear administrador", () -> {
            if (!contrasena.getText().equals(repetir.getText())) {
                throw new InventarioException("Las contraseñas no coinciden.");
            }
            return usuarios.crearAdministradorInicial(usuario.getText(), nombre.getText(),
                    contrasena.getText().toCharArray());
        });

        GridPane rejilla = Dialogos.rejilla();
        Dialogos.fila(rejilla, "Usuario", usuario);
        Dialogos.fila(rejilla, "Nombre completo", nombre);
        Dialogos.fila(rejilla, "Contraseña", contrasena);
        Dialogos.fila(rejilla, "Repetir", repetir);
        Label reglas = new Label("Al menos 8 caracteres, combinando letras y números.");
        reglas.getStyleClass().add("texto-secundario");
        Platform.runLater(nombre::requestFocus);
        return new VBox(16, encabezado("Primera ejecución: cree el usuario administrador."), rejilla, reglas,
                error, crear);
    }

    /** Botón que ejecuta la acción; Enter en cualquier campo también lo activa. */
    private Button botonPrincipal(String texto, Supplier<Usuario> accion) {
        Button boton = new Button(texto);
        boton.getStyleClass().add("primario");
        boton.setDefaultButton(true);
        boton.setMaxWidth(Double.MAX_VALUE);
        boton.setOnAction(e -> {
            try {
                error.setText("");
                alEntrar.accept(accion.get());
            } catch (InventarioException ex) {
                error.setText(ex.getMessage());
            }
        });
        return boton;
    }

    private static VBox encabezado(String texto) {
        Label titulo = new Label("Administrador de Inventario");
        titulo.getStyleClass().add("titulo-acceso");
        Label subtitulo = new Label(texto);
        subtitulo.getStyleClass().add("texto-secundario");
        subtitulo.setWrapText(true);
        VBox caja = new VBox(4, titulo, subtitulo);
        caja.setAlignment(Pos.CENTER_LEFT);
        return caja;
    }
}
