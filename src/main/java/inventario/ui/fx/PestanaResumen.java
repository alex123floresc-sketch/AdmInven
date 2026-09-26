package inventario.ui.fx;

import inventario.modelo.Movimiento;
import inventario.modelo.Producto;
import inventario.servicio.InventarioServicio;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Vista general: indicadores clave, productos que hay que reponer y actividad reciente. */
final class PestanaResumen implements Seccion {

    private final InventarioServicio servicio;
    private final Label productos = new Label();
    private final Label unidades = new Label();
    private final Label valor = new Label();
    private final Label stockBajo = new Label();
    private final VBox tarjetaStockBajo;
    private final TableView<Producto> reponer = Tablas.nueva("Todos los productos tienen stock suficiente.");
    private final Map<String, String> nombres = new HashMap<>();
    private final TableView<Movimiento> recientes;
    private final ScrollPane vista;

    PestanaResumen(InventarioServicio servicio) {
        this.servicio = servicio;
        tarjetaStockBajo = tarjeta("Con stock bajo", stockBajo);
        HBox tarjetas = new HBox(16, tarjeta("Productos activos", productos), tarjeta("Unidades en stock", unidades),
                tarjeta("Valor del inventario", valor), tarjetaStockBajo);
        tarjetas.getChildren().forEach(t -> HBox.setHgrow(t, Priority.ALWAYS));

        reponer.getColumns().add(Tablas.texto("Código", Producto::getCodigo, 80));
        reponer.getColumns().add(Tablas.texto("Nombre", Producto::getNombre, 180));
        reponer.getColumns().add(Tablas.numero("Stock", Producto::getStock, Formatos::entero, 60));
        reponer.getColumns().add(Tablas.numero("Mínimo", Producto::getStockMinimo, Formatos::entero, 60));
        recientes = Tablas.movimientos(c -> nombres.getOrDefault(c, "?"), "Aún no hay movimientos.");
        // En el resumen basta con el nombre del producto; el código solo quita espacio.
        recientes.getColumns().removeIf(c -> c.getText().equals("Código"));

        VBox panelReponer = panel("Para reponer", reponer);
        VBox panelRecientes = panel("Últimos movimientos", recientes);
        HBox.setHgrow(panelReponer, Priority.ALWAYS);
        HBox.setHgrow(panelRecientes, Priority.ALWAYS);
        panelReponer.setPrefWidth(380);
        panelRecientes.setPrefWidth(620);
        HBox paneles = new HBox(16, panelReponer, panelRecientes);
        VBox.setVgrow(paneles, Priority.ALWAYS);

        VBox contenido = new VBox(16, tarjetas, paneles);
        contenido.setPadding(new Insets(16));
        vista = new ScrollPane(contenido);
        vista.setFitToWidth(true);
        vista.setFitToHeight(true);
        vista.getStyleClass().add("sin-borde");
    }

    @Override
    public String titulo() {
        return "Resumen";
    }

    @Override
    public Node vista() {
        return vista;
    }

    @Override
    public void refrescar() {
        List<Producto> activos = servicio.listarProductos();
        List<Producto> bajos = servicio.productosConStockBajo();
        nombres.clear();
        activos.forEach(p -> nombres.put(p.getCodigo(), p.getNombre()));
        servicio.listarProductosDadosDeBaja().forEach(p -> nombres.put(p.getCodigo(), p.getNombre() + " (baja)"));

        productos.setText(Formatos.entero(activos.size()));
        unidades.setText(Formatos.entero(servicio.unidadesTotales()));
        valor.setText(Formatos.moneda(servicio.valorTotalInventario()));
        stockBajo.setText(Formatos.entero(bajos.size()));
        tarjetaStockBajo.getStyleClass().remove("tarjeta-alerta");
        if (!bajos.isEmpty()) {
            tarjetaStockBajo.getStyleClass().add("tarjeta-alerta");
        }
        reponer.getItems().setAll(bajos);
        recientes.getItems().setAll(servicio.ultimosMovimientos(15));
    }

    private static VBox tarjeta(String titulo, Label valor) {
        Label etiqueta = new Label(titulo);
        etiqueta.getStyleClass().add("tarjeta-titulo");
        valor.getStyleClass().add("tarjeta-valor");
        VBox tarjeta = new VBox(6, etiqueta, valor);
        tarjeta.getStyleClass().add("tarjeta");
        tarjeta.setMinWidth(160);
        return tarjeta;
    }

    private static VBox panel(String titulo, Node contenido) {
        Label etiqueta = new Label(titulo);
        etiqueta.getStyleClass().add("titulo-panel");
        VBox.setVgrow(contenido, Priority.ALWAYS);
        VBox panel = new VBox(8, etiqueta, contenido);
        panel.getStyleClass().add("panel");
        return panel;
    }
}
