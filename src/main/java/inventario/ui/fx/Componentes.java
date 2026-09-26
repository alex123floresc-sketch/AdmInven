package inventario.ui.fx;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Bloques visuales reutilizados por varias pestañas. */
final class Componentes {

    private Componentes() {
    }

    /** Indicador grande con su título (p. ej. "Ventas: S/ 1,234.00"). */
    static VBox tarjeta(String titulo, Label valor) {
        Label etiqueta = new Label(titulo);
        etiqueta.getStyleClass().add("tarjeta-titulo");
        valor.getStyleClass().add("tarjeta-valor");
        VBox tarjeta = new VBox(6, etiqueta, valor);
        tarjeta.getStyleClass().add("tarjeta");
        tarjeta.setMinWidth(150);
        return tarjeta;
    }

    /** Recuadro blanco con título que agrupa una tabla o un gráfico. */
    static VBox panel(String titulo, Node contenido) {
        Label etiqueta = new Label(titulo);
        etiqueta.getStyleClass().add("titulo-panel");
        VBox.setVgrow(contenido, Priority.ALWAYS);
        VBox panel = new VBox(8, etiqueta, contenido);
        panel.getStyleClass().add("panel");
        return panel;
    }
}
