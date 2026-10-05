package inventario.ui.fx;

import inventario.modelo.Producto;
import inventario.servicio.InventarioException;
import inventario.servicio.InventarioServicio;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Venta rápida de mostrador. Un lector de código de barras funciona como un teclado: escribe el código y pulsa
 * Enter, así cada lectura agrega una unidad al ticket. «Cobrar» registra todas las líneas juntas (o ninguna).
 */
final class PestanaVenta implements Seccion {

    /** "3*ABC" agrega 3 unidades de ABC. */
    private static final Pattern CON_CANTIDAD = Pattern.compile("^(\\d{1,6})\\s*\\*\\s*(.+)$");

    /** Una línea del ticket; el producto guarda el precio y el stock leídos al agregarla. */
    record Linea(Producto producto, int cantidad) {

        BigDecimal subtotal() {
            return producto.getPrecio().multiply(BigDecimal.valueOf(cantidad));
        }
    }

    private final InventarioServicio servicio;
    private final Runnable alVender;
    private final ObservableList<Linea> lineas = FXCollections.observableArrayList();
    private final TableView<Linea> tabla;
    private final TextField codigo = new TextField();
    private final Label mensaje = new Label();
    private final Label total = new Label();
    private final BorderPane vista = new BorderPane();

    PestanaVenta(InventarioServicio servicio, Runnable alVender) {
        this.servicio = servicio;
        this.alVender = alVender;

        codigo.setPromptText("Escanee o escriba el código y pulse Enter");
        codigo.setPrefWidth(360);
        codigo.setOnAction(e -> agregar());
        Label ayuda = new Label("Varias unidades: 3*CÓDIGO. También puede escribir parte del nombre.");
        ayuda.getStyleClass().add("texto-secundario");
        HBox entrada = new HBox(12, codigo, ayuda);
        entrada.setAlignment(Pos.CENTER_LEFT);
        mensaje.setWrapText(true);
        mensaje.managedProperty().bind(mensaje.textProperty().isNotEmpty());
        mensaje.visibleProperty().bind(mensaje.textProperty().isNotEmpty());
        VBox arriba = new VBox(10, entrada, mensaje);
        arriba.setPadding(new Insets(0, 0, 12, 0));

        tabla = Tablas.nueva("El ticket está vacío. Escanee un producto para empezar.");
        tabla.getColumns().add(Tablas.texto("Código", l -> l.producto().getCodigo(), 120));
        tabla.getColumns().add(Tablas.texto("Producto", l -> l.producto().getNombre(), 320));
        tabla.getColumns().add(Tablas.numero("Cantidad", Linea::cantidad, Formatos::entero, 90));
        tabla.getColumns().add(Tablas.numero("Precio", l -> l.producto().getPrecio(), Formatos::moneda, 110));
        tabla.getColumns().add(Tablas.numero("Subtotal", Linea::subtotal, Formatos::moneda, 120));
        tabla.setItems(lineas);
        tabla.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) {
                quitarSeleccionada();
            }
        });

        Button quitar = new Button("Quitar línea");
        quitar.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());
        quitar.setOnAction(e -> quitarSeleccionada());
        Button vaciar = new Button("Vaciar ticket");
        vaciar.disableProperty().bind(Bindings.isEmpty(lineas));
        vaciar.setOnAction(e -> {
            lineas.clear();
            mostrar("", false);
            codigo.requestFocus();
        });
        Button cobrar = new Button("Cobrar");
        cobrar.getStyleClass().add("primario");
        cobrar.disableProperty().bind(Bindings.isEmpty(lineas));
        cobrar.setOnAction(e -> cobrar());
        total.getStyleClass().add("total-ticket");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox pie = new HBox(10, quitar, vaciar, espacio, total, cobrar);
        pie.setAlignment(Pos.CENTER_LEFT);
        pie.setPadding(new Insets(12, 0, 0, 0));

        vista.setTop(arriba);
        vista.setCenter(tabla);
        vista.setBottom(pie);
        vista.setPadding(new Insets(16));
        actualizarTotal();
    }

    @Override
    public String titulo() {
        return "Venta rápida";
    }

    @Override
    public Node vista() {
        return vista;
    }

    /** Relee precio y stock de cada línea; las de productos dados de baja se quitan. */
    @Override
    public void refrescar() {
        List<Linea> vigentes = new ArrayList<>();
        for (Linea l : lineas) {
            try {
                vigentes.add(new Linea(servicio.obtenerActivo(l.producto().getCodigo()), l.cantidad()));
            } catch (InventarioException e) {
                // El producto ya no está disponible: sale del ticket.
            }
        }
        lineas.setAll(vigentes);
        actualizarTotal();
        Platform.runLater(codigo::requestFocus);
    }

    private void agregar() {
        String texto = codigo.getText() == null ? "" : codigo.getText().strip();
        codigo.clear();
        if (texto.isEmpty()) {
            return;
        }
        int cantidad = 1;
        Matcher m = CON_CANTIDAD.matcher(texto);
        if (m.matches()) {
            cantidad = Integer.parseInt(m.group(1));
            texto = m.group(2).strip();
        }
        try {
            if (cantidad <= 0) {
                throw new InventarioException("La cantidad debe ser mayor que cero.");
            }
            Producto producto = buscarProducto(texto);
            int indice = indiceDe(producto);
            int nuevaCantidad = cantidad + (indice < 0 ? 0 : lineas.get(indice).cantidad());
            if (nuevaCantidad > producto.getStock()) {
                throw new InventarioException("Stock insuficiente: hay " + producto.getStock() + " unidades de "
                        + producto.getNombre() + ".");
            }
            Linea linea = new Linea(producto, nuevaCantidad);
            if (indice < 0) {
                lineas.add(linea);
                indice = lineas.size() - 1;
            } else {
                lineas.set(indice, linea);
            }
            tabla.getSelectionModel().select(indice);
            tabla.scrollTo(indice);
            mostrar("", false);
        } catch (InventarioException e) {
            mostrar(e.getMessage(), true);
        }
        actualizarTotal();
    }

    /** Por código exacto (lo que envía el lector) o, si no existe, por una única coincidencia en el nombre. */
    private Producto buscarProducto(String texto) {
        try {
            return servicio.obtenerActivo(texto);
        } catch (InventarioException noEsCodigo) {
            List<Producto> encontrados = servicio.buscar(texto);
            if (encontrados.size() == 1) {
                return encontrados.getFirst();
            }
            if (encontrados.isEmpty()) {
                throw noEsCodigo;
            }
            throw new InventarioException(encontrados.size() + " productos coinciden con \"" + texto
                    + "\". Escriba el código o más letras del nombre.");
        }
    }

    private int indiceDe(Producto producto) {
        for (int i = 0; i < lineas.size(); i++) {
            if (lineas.get(i).producto().getCodigo().equals(producto.getCodigo())) {
                return i;
            }
        }
        return -1;
    }

    private void quitarSeleccionada() {
        Linea elegida = tabla.getSelectionModel().getSelectedItem();
        if (elegida != null) {
            lineas.remove(elegida);
            actualizarTotal();
        }
        codigo.requestFocus();
    }

    private void cobrar() {
        BigDecimal importe = totalTicket();
        List<InventarioServicio.ItemVenta> items = lineas.stream()
                .map(l -> new InventarioServicio.ItemVenta(l.producto().getCodigo(), l.cantidad()))
                .toList();
        try {
            List<Producto> vendidos = servicio.registrarVenta(items, "Venta rápida");
            lineas.clear();
            StringBuilder texto = new StringBuilder("Venta registrada: " + Formatos.moneda(importe) + ".");
            vendidos.stream().filter(Producto::tieneStockBajo).forEach(p -> texto.append(" Stock bajo: ")
                    .append(p.getNombre()).append(" (quedan ").append(p.getStock()).append(")."));
            alVender.run();
            mostrar(texto.toString(), false);
        } catch (InventarioException e) {
            mostrar(e.getMessage(), true);
        }
        actualizarTotal();
        codigo.requestFocus();
    }

    private BigDecimal totalTicket() {
        return lineas.stream().map(Linea::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void actualizarTotal() {
        int unidades = lineas.stream().mapToInt(Linea::cantidad).sum();
        total.setText("Total: " + Formatos.moneda(totalTicket()) + "  ·  " + unidades + " unidad(es)");
    }

    private void mostrar(String texto, boolean esError) {
        mensaje.getStyleClass().removeAll("mensaje-error", "mensaje-exito");
        mensaje.getStyleClass().add(esError ? "mensaje-error" : "mensaje-exito");
        mensaje.setText(texto);
    }
}
