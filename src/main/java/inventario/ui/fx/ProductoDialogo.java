package inventario.ui.fx;

import inventario.modelo.Producto;
import inventario.servicio.InventarioServicio;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/** Formulario para registrar un producto nuevo o editar uno existente. */
final class ProductoDialogo {

    private ProductoDialogo() {
    }

    static Optional<Producto> nuevo(Window duenio, InventarioServicio servicio, List<String> categorias) {
        return mostrar(duenio, servicio, null, categorias);
    }

    static Optional<Producto> editar(Window duenio, InventarioServicio servicio, Producto producto,
                                     List<String> categorias) {
        return mostrar(duenio, servicio, producto, categorias);
    }

    private static Optional<Producto> mostrar(Window duenio, InventarioServicio servicio, Producto actual,
                                              List<String> categorias) {
        boolean nuevo = actual == null;
        TextField codigo = campo(nuevo ? "" : actual.getCodigo(), "Ej. ARR-01");
        codigo.setDisable(!nuevo);
        TextField nombre = campo(nuevo ? "" : actual.getNombre(), "Nombre del producto");
        ComboBox<String> categoria = new ComboBox<>();
        categoria.getItems().setAll(categorias);
        categoria.setEditable(true);
        categoria.setPromptText("General");
        categoria.getEditor().setText(nuevo ? "" : actual.getCategoria());
        TextField precio = campo(nuevo ? "" : actual.getPrecio().toPlainString(), "0.00");
        TextField costo = campo(nuevo ? "" : actual.getCosto().toPlainString(), "0.00");
        Label margen = new Label();
        margen.getStyleClass().add("texto-secundario");
        precio.textProperty().addListener((o, a, n) -> mostrarMargen(precio, costo, margen));
        costo.textProperty().addListener((o, a, n) -> mostrarMargen(precio, costo, margen));
        mostrarMargen(precio, costo, margen);
        TextField stockInicial = campo("0", "0");
        TextField stockMinimo = campo(nuevo ? "0" : String.valueOf(actual.getStockMinimo()), "0");

        GridPane rejilla = Dialogos.rejilla();
        Dialogos.fila(rejilla, "Código", codigo);
        Dialogos.fila(rejilla, "Nombre", nombre);
        Dialogos.fila(rejilla, "Categoría", categoria);
        Dialogos.fila(rejilla, "Precio de venta (" + Formatos.MONEDA + ")", precio);
        Dialogos.fila(rejilla, "Costo (" + Formatos.MONEDA + ")", costo);
        Dialogos.fila(rejilla, "", margen);
        if (nuevo) {
            Dialogos.fila(rejilla, "Stock inicial", stockInicial);
        }
        Dialogos.fila(rejilla, "Stock mínimo", stockMinimo);
        Platform.runLater(nuevo ? codigo::requestFocus : nombre::requestFocus);

        return Dialogos.formulario(duenio, nuevo ? "Nuevo producto" : "Editar " + actual.getCodigo(),
                "Guardar", rejilla, () -> {
                    BigDecimal valorPrecio = Formatos.leerDecimal("El precio", precio.getText());
                    BigDecimal valorCosto = costo.getText().isBlank() ? BigDecimal.ZERO
                            : Formatos.leerDecimal("El costo", costo.getText());
                    int minimo = Formatos.leerEntero("El stock mínimo", stockMinimo.getText());
                    String textoCategoria = categoria.getEditor().getText();
                    if (nuevo) {
                        int inicial = Formatos.leerEntero("El stock inicial", stockInicial.getText());
                        return servicio.registrarProducto(codigo.getText(), nombre.getText(), textoCategoria,
                                valorPrecio, valorCosto, inicial, minimo);
                    }
                    return servicio.actualizarProducto(actual.getCodigo(), nombre.getText(), textoCategoria,
                            valorPrecio, valorCosto, minimo);
                });
    }

    /** Muestra el margen mientras se escribe, para detectar a tiempo un precio por debajo del costo. */
    private static void mostrarMargen(TextField precio, TextField costo, Label margen) {
        try {
            BigDecimal p = new BigDecimal(precio.getText().strip().replace(',', '.'));
            BigDecimal c = costo.getText().isBlank() ? BigDecimal.ZERO
                    : new BigDecimal(costo.getText().strip().replace(',', '.'));
            BigDecimal ganancia = p.subtract(c);
            String porcentaje = p.signum() == 0 ? "—"
                    : ganancia.multiply(BigDecimal.valueOf(100)).divide(p, 1, RoundingMode.HALF_UP) + " %";
            margen.setText("Margen: " + Formatos.moneda(ganancia) + " por unidad (" + porcentaje + ")"
                    + (ganancia.signum() < 0 ? "  — se vende a pérdida" : ""));
        } catch (NumberFormatException e) {
            margen.setText("Margen: —");
        }
    }

    private static TextField campo(String valor, String ayuda) {
        TextField campo = new TextField(valor);
        campo.setPromptText(ayuda);
        return campo;
    }
}
