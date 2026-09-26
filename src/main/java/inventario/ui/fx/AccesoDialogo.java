package inventario.ui.fx;

import inventario.Aplicacion;
import inventario.modelo.Usuario;
import inventario.servicio.InventarioException;
import inventario.servicio.UsuarioServicio;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

/** Inicio de sesión y configuración inicial (crear el primer administrador). */
final class AccesoDialogo {

    private AccesoDialogo() {
    }

    /** Pide usuario y contraseña; vacío si el usuario cancela. */
    static Optional<Usuario> iniciarSesion(Window duenio, UsuarioServicio usuarios, boolean demo) {
        TextField usuario = new TextField();
        usuario.setPromptText("usuario");
        PasswordField contrasena = new PasswordField();

        GridPane rejilla = Dialogos.rejilla();
        Dialogos.fila(rejilla, "Usuario", usuario);
        Dialogos.fila(rejilla, "Contraseña", contrasena);
        VBox contenido = new VBox(14, encabezado("Inicie sesión para continuar."), rejilla);
        if (demo) {
            Label ayuda = new Label("Usuarios de demostración: " + Aplicacion.CREDENCIALES_DEMO);
            ayuda.getStyleClass().add("aviso-demo");
            ayuda.setWrapText(true);
            contenido.getChildren().add(ayuda);
        }
        Platform.runLater(usuario::requestFocus);

        return Dialogos.formulario(duenio, "Iniciar sesión", "Entrar", contenido, () -> {
            try {
                return usuarios.iniciarSesion(usuario.getText(), contrasena.getText().toCharArray());
            } catch (InventarioException e) {
                contrasena.clear();
                contrasena.requestFocus();
                throw e;
            }
        });
    }

    /** Primera ejecución: crea el administrador y deja su sesión iniciada. */
    static Optional<Usuario> configuracionInicial(Window duenio, UsuarioServicio usuarios) {
        TextField usuario = new TextField("admin");
        TextField nombre = new TextField();
        nombre.setPromptText("Ej. Ana Torres");
        PasswordField contrasena = new PasswordField();
        PasswordField repetir = new PasswordField();

        GridPane rejilla = Dialogos.rejilla();
        Dialogos.fila(rejilla, "Usuario", usuario);
        Dialogos.fila(rejilla, "Nombre completo", nombre);
        Dialogos.fila(rejilla, "Contraseña", contrasena);
        Dialogos.fila(rejilla, "Repetir contraseña", repetir);
        Label reglas = new Label("La contraseña debe tener al menos 8 caracteres y combinar letras y números.");
        reglas.getStyleClass().add("texto-secundario");
        reglas.setWrapText(true);
        VBox contenido = new VBox(14,
                encabezado("Primera ejecución: cree el usuario administrador. Podrá registrar más usuarios después."),
                rejilla, reglas);
        Platform.runLater(nombre::requestFocus);

        return Dialogos.formulario(duenio, "Configuración inicial", "Crear administrador", contenido, () -> {
            if (!contrasena.getText().equals(repetir.getText())) {
                throw new InventarioException("Las contraseñas no coinciden.");
            }
            return usuarios.crearAdministradorInicial(usuario.getText(), nombre.getText(),
                    contrasena.getText().toCharArray());
        });
    }

    private static VBox encabezado(String texto) {
        Label titulo = new Label("Administrador de Inventario");
        titulo.getStyleClass().add("titulo-acceso");
        Label subtitulo = new Label(texto);
        subtitulo.getStyleClass().add("texto-secundario");
        subtitulo.setWrapText(true);
        return new VBox(4, titulo, subtitulo);
    }
}
