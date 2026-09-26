package inventario.ui.fx;

import inventario.modelo.Movimiento;
import inventario.modelo.Producto;
import inventario.modelo.TipoMovimiento;
import inventario.servicio.InventarioServicio;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Registro completo de movimientos de stock, del más reciente al más antiguo. */
final class PestanaMovimientos implements Seccion {

    private static final String TODOS = "Todos los tipos";

    private final InventarioServicio servicio;
    private final Map<String, String> nombres = new HashMap<>();
    private final ObservableList<Movimiento> datos = FXCollections.observableArrayList();
    private final FilteredList<Movimiento> filtrados = new FilteredList<>(datos);
    private final TextField buscar = new TextField();
    private final ComboBox<String> tipo = new ComboBox<>();
    private final Label contador = new Label();
    private final BorderPane vista = new BorderPane();

    PestanaMovimientos(InventarioServicio servicio) {
        this.servicio = servicio;
        TableView<Movimiento> tabla = Tablas.movimientos(c -> nombres.getOrDefault(c, "?"),
                "No hay movimientos que coincidan.");
        tabla.setItems(filtrados);

        buscar.setPromptText("Buscar por código, producto o nota");
        buscar.setPrefWidth(300);
        buscar.textProperty().addListener((o, a, n) -> aplicarFiltro());
        tipo.getItems().setAll(TODOS, "Entrada", "Salida", "Ajuste");
        tipo.setValue(TODOS);
        tipo.setOnAction(e -> aplicarFiltro());
        HBox barra = new HBox(10, buscar, tipo);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.setPadding(new Insets(0, 0, 12, 0));

        contador.getStyleClass().add("texto-secundario");
        HBox pie = new HBox(contador);
        pie.setPadding(new Insets(8, 0, 0, 0));
        vista.setTop(barra);
        vista.setCenter(tabla);
        vista.setBottom(pie);
        vista.setPadding(new Insets(16));
    }

    @Override
    public String titulo() {
        return "Movimientos";
    }

    @Override
    public Node vista() {
        return vista;
    }

    @Override
    public void refrescar() {
        nombres.clear();
        Stream.concat(servicio.listarProductos().stream(), servicio.listarProductosDadosDeBaja().stream())
                .forEach(p -> nombres.put(p.getCodigo(), nombreConEstado(p)));
        datos.setAll(servicio.listarMovimientos());
        aplicarFiltro();
    }

    private void aplicarFiltro() {
        String texto = buscar.getText() == null ? "" : buscar.getText().strip().toLowerCase(Locale.ROOT);
        TipoMovimiento elegido = switch (tipo.getValue()) {
            case "Entrada" -> TipoMovimiento.ENTRADA;
            case "Salida" -> TipoMovimiento.SALIDA;
            case "Ajuste" -> TipoMovimiento.AJUSTE;
            default -> null;
        };
        filtrados.setPredicate(m -> (elegido == null || m.tipo() == elegido)
                && (texto.isEmpty()
                || m.codigoProducto().toLowerCase(Locale.ROOT).contains(texto)
                || nombres.getOrDefault(m.codigoProducto(), "").toLowerCase(Locale.ROOT).contains(texto)
                || m.nota().toLowerCase(Locale.ROOT).contains(texto)));
        contador.setText(filtrados.size() + " de " + datos.size() + " movimiento(s)");
    }

    private static String nombreConEstado(Producto p) {
        return p.isActivo() ? p.getNombre() : p.getNombre() + " (baja)";
    }
}
