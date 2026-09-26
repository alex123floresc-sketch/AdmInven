package inventario.ui.fx;

import inventario.modelo.Producto;
import inventario.servicio.InventarioServicio;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

import java.math.BigDecimal;
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
        TextField stockInicial = campo("0", "0");
        TextField stockMinimo = campo(nuevo ? "0" : String.valueOf(actual.getStockMinimo()), "0");

        GridPane rejilla = Dialogos.rejilla();
        Dialogos.fila(rejilla, "Código", codigo);
        Dialogos.fila(rejilla, "Nombre", nombre);
        Dialogos.fila(rejilla, "Categoría", categoria);
        Dialogos.fila(rejilla, "Precio (" + Formatos.MONEDA + ")", precio);
        if (nuevo) {
            Dialogos.fila(rejilla, "Stock inicial", stockInicial);
        }
        Dialogos.fila(rejilla, "Stock mínimo", stockMinimo);
        Platform.runLater(nuevo ? codigo::requestFocus : nombre::requestFocus);

        return Dialogos.formulario(duenio, nuevo ? "Nuevo producto" : "Editar " + actual.getCodigo(),
                "Guardar", rejilla, () -> {
                    BigDecimal valorPrecio = Formatos.leerDecimal("El precio", precio.getText());
                    int minimo = Formatos.leerEntero("El stock mínimo", stockMinimo.getText());
                    String textoCategoria = categoria.getEditor().getText();
                    if (nuevo) {
                        int inicial = Formatos.leerEntero("El stock inicial", stockInicial.getText());
                        return servicio.registrarProducto(codigo.getText(), nombre.getText(), textoCategoria,
                                valorPrecio, inicial, minimo);
                    }
                    return servicio.actualizarProducto(actual.getCodigo(), nombre.getText(), textoCategoria,
                            valorPrecio, minimo);
                });
    }

    private static TextField campo(String valor, String ayuda) {
        TextField campo = new TextField(valor);
        campo.setPromptText(ayuda);
        return campo;
    }
}
