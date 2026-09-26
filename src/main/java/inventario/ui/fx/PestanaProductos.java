package inventario.ui.fx;

import inventario.modelo.Movimiento;
import inventario.modelo.Producto;
import inventario.modelo.TipoMovimiento;
import inventario.servicio.InventarioException;
import inventario.servicio.InventarioServicio;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.Locale;

/** Catálogo de productos: búsqueda, alta, edición, movimientos de stock y bajas. */
final class PestanaProductos implements Seccion {

    private static final PseudoClass STOCK_BAJO = PseudoClass.getPseudoClass("stock-bajo");
    private static final PseudoClass INACTIVO = PseudoClass.getPseudoClass("inactivo");
    private static final String TODAS = "Todas las categorías";

    private final InventarioServicio servicio;
    private final Runnable alCambiar;
    private final ObservableList<Producto> datos = FXCollections.observableArrayList();
    private final FilteredList<Producto> filtrados = new FilteredList<>(datos);
    private final TableView<Producto> tabla = Tablas.nueva("No hay productos que coincidan.");
    private final TextField buscar = new TextField();
    private final ComboBox<String> categoria = new ComboBox<>();
    private final CheckBox verBajas = new CheckBox("Ver dados de baja");
    private final Label contador = new Label();
    private final Button bajaOReactivar = new Button("Dar de baja");
    private final BorderPane vista = new BorderPane();

    PestanaProductos(InventarioServicio servicio, Runnable alCambiar) {
        this.servicio = servicio;
        this.alCambiar = alCambiar;
        construirTabla();
        vista.setTop(construirBarra());
        vista.setCenter(tabla);
        contador.getStyleClass().add("texto-secundario");
        HBox pie = new HBox(contador);
        pie.setPadding(new Insets(8, 0, 0, 0));
        vista.setBottom(pie);
        vista.setPadding(new Insets(16));
    }

    @Override
    public String titulo() {
        return "Productos";
    }

    @Override
    public Node vista() {
        return vista;
    }

    @Override
    public void refrescar() {
        Producto seleccionado = tabla.getSelectionModel().getSelectedItem();
        datos.setAll(verBajas.isSelected() ? servicio.listarProductosDadosDeBaja() : servicio.listarProductos());
        String categoriaElegida = categoria.getValue();
        categoria.getItems().setAll(TODAS);
        categoria.getItems().addAll(servicio.listarCategorias());
        categoria.setValue(categoria.getItems().contains(categoriaElegida) ? categoriaElegida : TODAS);
        if (seleccionado != null) {
            datos.stream().filter(p -> p.getCodigo().equals(seleccionado.getCodigo())).findFirst()
                    .ifPresent(p -> tabla.getSelectionModel().select(p));
        }
        actualizarContador();
    }

    private Node construirBarra() {
        buscar.setPromptText("Buscar por código, nombre o categoría");
        buscar.setPrefWidth(300);
        buscar.textProperty().addListener((o, a, n) -> aplicarFiltro());
        categoria.setOnAction(e -> aplicarFiltro());
        verBajas.setOnAction(e -> {
            bajaOReactivar.setText(verBajas.isSelected() ? "Reactivar" : "Dar de baja");
            refrescar();
        });
        HBox filtros = new HBox(10, buscar, categoria, verBajas);
        filtros.setAlignment(Pos.CENTER_LEFT);

        Button nuevo = new Button("Nuevo producto");
        nuevo.getStyleClass().add("primario");
        nuevo.setOnAction(e -> accion(() -> ProductoDialogo.nuevo(ventana(), servicio, servicio.listarCategorias())
                .ifPresent(p -> cambioRealizado())));
        Button editar = new Button("Editar");
        editar.setOnAction(e -> editarSeleccionado());
        Button entrada = new Button("Entrada");
        entrada.setOnAction(e -> movimiento(TipoMovimiento.ENTRADA));
        Button salida = new Button("Salida");
        salida.setOnAction(e -> movimiento(TipoMovimiento.SALIDA));
        Button ajustar = new Button("Ajustar");
        ajustar.setOnAction(e -> movimiento(TipoMovimiento.AJUSTE));
        Button historial = new Button("Historial");
        historial.setOnAction(e -> mostrarHistorial());
        bajaOReactivar.getStyleClass().add("peligro");
        bajaOReactivar.setOnAction(e -> bajaOReactivar());

        BooleanBinding sinSeleccion = tabla.getSelectionModel().selectedItemProperty().isNull();
        BooleanBinding soloActivos = sinSeleccion.or(verBajas.selectedProperty());
        editar.disableProperty().bind(soloActivos);
        entrada.disableProperty().bind(soloActivos);
        salida.disableProperty().bind(soloActivos);
        ajustar.disableProperty().bind(soloActivos);
        historial.disableProperty().bind(sinSeleccion);
        bajaOReactivar.disableProperty().bind(sinSeleccion);
        nuevo.disableProperty().bind(verBajas.selectedProperty());

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox acciones = new HBox(8, nuevo, editar, separador(), entrada, salida, ajustar, separador(),
                historial, espacio, bajaOReactivar);
        acciones.setAlignment(Pos.CENTER_LEFT);

        VBox barra = new VBox(12, filtros, acciones);
        barra.setPadding(new Insets(0, 0, 12, 0));
        return barra;
    }

    private void construirTabla() {
        tabla.getColumns().add(Tablas.texto("Código", Producto::getCodigo, 90));
        tabla.getColumns().add(Tablas.texto("Nombre", Producto::getNombre, 240));
        tabla.getColumns().add(Tablas.texto("Categoría", Producto::getCategoria, 130));
        tabla.getColumns().add(Tablas.numero("Precio", Producto::getPrecio, Formatos::numero, 90));
        tabla.getColumns().add(Tablas.numero("Stock", Producto::getStock, Formatos::entero, 70));
        tabla.getColumns().add(Tablas.numero("Mínimo", Producto::getStockMinimo, Formatos::entero, 70));
        tabla.getColumns().add(Tablas.numero("Valor en stock", Producto::valorEnStock, Formatos::numero, 110));

        SortedList<Producto> ordenados = new SortedList<>(filtrados);
        ordenados.comparatorProperty().bind(tabla.comparatorProperty());
        tabla.setItems(ordenados);
        tabla.setRowFactory(t -> {
            TableRow<Producto> fila = new TableRow<>() {
                @Override
                protected void updateItem(Producto p, boolean vacio) {
                    super.updateItem(p, vacio);
                    pseudoClassStateChanged(STOCK_BAJO, !vacio && p != null && p.isActivo() && p.tieneStockBajo());
                    pseudoClassStateChanged(INACTIVO, !vacio && p != null && !p.isActivo());
                }
            };
            fila.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !fila.isEmpty()
                        && !verBajas.isSelected()) {
                    editarSeleccionado();
                }
            });
            return fila;
        });
    }

    private void aplicarFiltro() {
        String texto = buscar.getText() == null ? "" : buscar.getText().strip().toLowerCase(Locale.ROOT);
        String cat = categoria.getValue();
        filtrados.setPredicate(p -> (cat == null || cat.equals(TODAS) || p.getCategoria().equals(cat))
                && (texto.isEmpty()
                || p.getCodigo().toLowerCase(Locale.ROOT).contains(texto)
                || p.getNombre().toLowerCase(Locale.ROOT).contains(texto)
                || p.getCategoria().toLowerCase(Locale.ROOT).contains(texto)));
        actualizarContador();
    }

    private void actualizarContador() {
        long bajos = filtrados.stream().filter(p -> p.isActivo() && p.tieneStockBajo()).count();
        contador.setText(filtrados.size() + " de " + datos.size() + " producto(s)"
                + (bajos > 0 ? "  ·  " + bajos + " con stock bajo (resaltados)" : ""));
    }

    private void editarSeleccionado() {
        Producto p = tabla.getSelectionModel().getSelectedItem();
        if (p != null) {
            accion(() -> ProductoDialogo.editar(ventana(), servicio, p, servicio.listarCategorias())
                    .ifPresent(x -> cambioRealizado()));
        }
    }

    private void movimiento(TipoMovimiento tipo) {
        Producto p = tabla.getSelectionModel().getSelectedItem();
        if (p != null) {
            accion(() -> MovimientoDialogo.mostrar(ventana(), servicio, p, tipo).ifPresent(actualizado -> {
                cambioRealizado();
                if (tipo == TipoMovimiento.SALIDA && actualizado.tieneStockBajo()) {
                    Dialogos.informacion(ventana(), "Stock bajo", actualizado.getNombre() + " quedó con "
                            + actualizado.getStock() + " unidades (mínimo " + actualizado.getStockMinimo() + ").");
                }
            }));
        }
    }

    private void bajaOReactivar() {
        Producto p = tabla.getSelectionModel().getSelectedItem();
        if (p == null) {
            return;
        }
        accion(() -> {
            if (verBajas.isSelected()) {
                servicio.reactivarProducto(p.getCodigo());
                cambioRealizado();
            } else if (Dialogos.confirmar(ventana(), "Dar de baja", "¿Dar de baja \"" + p.getNombre()
                    + "\"?\nSaldrá del catálogo, pero su historial se conserva y podrá reactivarlo.", "Dar de baja")) {
                servicio.darDeBajaProducto(p.getCodigo());
                cambioRealizado();
            }
        });
    }

    private void mostrarHistorial() {
        Producto p = tabla.getSelectionModel().getSelectedItem();
        if (p == null) {
            return;
        }
        List<Movimiento> historial = servicio.historial(p.getCodigo()).reversed();
        TableView<Movimiento> tablaHistorial = Tablas.movimientos(null, "Sin movimientos registrados.");
        tablaHistorial.getItems().setAll(historial);
        tablaHistorial.setPrefSize(640, 380);

        Dialog<Void> dialogo = new Dialog<>();
        Dialogos.preparar(dialogo, ventana(), "Historial de " + p.getCodigo());
        Label encabezado = new Label(p.getNombre() + "  ·  stock actual " + p.getStock());
        encabezado.getStyleClass().add("texto-destacado");
        dialogo.getDialogPane().setContent(new VBox(10, encabezado, tablaHistorial));
        dialogo.getDialogPane().getButtonTypes().add(Dialogos.CERRAR);
        dialogo.setResizable(true);
        dialogo.showAndWait();
    }

    private void cambioRealizado() {
        alCambiar.run();
    }

    private void accion(Runnable accion) {
        try {
            accion.run();
        } catch (InventarioException e) {
            Dialogos.error(ventana(), e.getMessage());
        }
    }

    private Window ventana() {
        return vista.getScene().getWindow();
    }

    private static Node separador() {
        Region r = new Region();
        r.getStyleClass().add("separador-vertical");
        return r;
    }
}
