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
import javafx.scene.control.Control;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;
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

    /** Tarjeta de dos paneles: la presentación del programa a la izquierda y el formulario a la derecha. */
    Parent vista() {
        VBox formulario = usuarios.requiereConfiguracionInicial() ? formularioInicial() : formularioAcceso();
        formulario.getStyleClass().add("formulario-acceso");
        formulario.setPrefWidth(380);
        HBox tarjeta = new HBox(panelMarca(), formulario);
        tarjeta.getStyleClass().add("tarjeta-acceso");
        tarjeta.setMaxWidth(Region.USE_PREF_SIZE);
        tarjeta.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane fondo = new StackPane(tarjeta);
        fondo.getStyleClass().add("fondo-acceso");
        fondo.setPadding(new Insets(24));
        return fondo;
    }

    private static VBox panelMarca() {
        ImageView icono = new ImageView(new Image(Objects.requireNonNull(
                PantallaAcceso.class.getResource("icono.png"), "falta icono.png").toExternalForm()));
        icono.setFitWidth(56);
        icono.setFitHeight(56);
        icono.setPreserveRatio(true);
        icono.setSmooth(true);
        Label nombre = new Label("Administrador\nde Inventario");
        nombre.getStyleClass().add("marca-nombre");
        Label lema = new Label("Stock, ventas y ganancias de su negocio en un solo lugar.");
        lema.getStyleClass().add("marca-lema");
        lema.setWrapText(true);
        VBox puntos = new VBox(8);
        for (String texto : List.of("Alertas de stock bajo", "Venta con lector de códigos",
                "Reportes de ganancia y margen")) {
            Label punto = new Label("✓  " + texto);
            punto.getStyleClass().add("marca-punto");
            puntos.getChildren().add(punto);
        }
        Region espacio = new Region();
        VBox.setVgrow(espacio, Priority.ALWAYS);
        VBox panel = new VBox(14, icono, nombre, lema, espacio, puntos);
        panel.getStyleClass().add("panel-marca");
        panel.setPrefWidth(260);
        return panel;
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

        VBox tarjeta = new VBox(16, encabezado("Bienvenido(a)", "Inicie sesión para continuar."),
                campo("Usuario", usuario), campo("Contraseña", contrasena), error, entrar);
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

        Label reglas = new Label("Al menos 8 caracteres, combinando letras y números.");
        reglas.getStyleClass().add("texto-secundario");
        Platform.runLater(nombre::requestFocus);
        return new VBox(12, encabezado("Primera ejecución", "Cree el usuario administrador."),
                campo("Usuario", usuario), campo("Nombre completo", nombre), campo("Contraseña", contrasena),
                campo("Repetir contraseña", repetir), reglas, error, crear);
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

    /** Etiqueta encima del campo, como en los formularios web. */
    private static VBox campo(String etiqueta, Control control) {
        Label texto = new Label(etiqueta);
        texto.getStyleClass().add("etiqueta-campo");
        texto.setLabelFor(control);
        control.setMaxWidth(Double.MAX_VALUE);
        control.getStyleClass().add("campo-acceso");
        return new VBox(6, texto, control);
    }

    private static VBox encabezado(String saludo, String texto) {
        Label titulo = new Label(saludo);
        titulo.getStyleClass().add("titulo-acceso");
        Label subtitulo = new Label(texto);
        subtitulo.getStyleClass().add("texto-secundario");
        subtitulo.setWrapText(true);
        VBox caja = new VBox(4, titulo, subtitulo);
        caja.setAlignment(Pos.CENTER_LEFT);
        return caja;
    }
}
