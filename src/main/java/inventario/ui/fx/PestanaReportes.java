package inventario.ui.fx;

import inventario.servicio.InventarioException;
import inventario.servicio.ReporteServicio;
import inventario.servicio.ReporteServicio.LineaCompra;
import inventario.servicio.ReporteServicio.LineaVenta;
import inventario.servicio.ReporteServicio.ProductoSinRotacion;
import inventario.servicio.ReporteServicio.ReporteVentas;
import inventario.servicio.ReporteServicio.VentaDiaria;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Reportes de ventas, productos sin rotación y compras por proveedor en un período elegido. */
final class PestanaReportes implements Seccion {

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    private final ReporteServicio servicio;
    private final DatePicker desde = new DatePicker();
    private final DatePicker hasta = new DatePicker();
    private final Label ingresos = new Label();
    private final Label costo = new Label();
    private final Label ganancia = new Label();
    private final Label margen = new Label();
    private final LineChart<Number, Number> graficoDiario;
    private final BarChart<Number, String> graficoTop;
    private final TableView<LineaVenta> tablaVentas = Tablas.nueva("No hubo ventas en el período.");
    private final Spinner<Integer> diasSinVenta = new Spinner<>(1, 365, 30, 5);
    private final TableView<ProductoSinRotacion> tablaSinRotacion =
            Tablas.nueva("Todos los productos con stock se vendieron en el plazo.");
    private final TableView<LineaCompra> tablaCompras = Tablas.nueva("No hubo compras en el período.");
    private final BorderPane vista = new BorderPane();
    private ReporteVentas ultimoReporte;

    PestanaReportes(ReporteServicio servicio) {
        this.servicio = servicio;
        LocalDate hoy = LocalDate.now();
        desde.setValue(hoy.minusDays(29));
        hasta.setValue(hoy);
        for (DatePicker selector : List.of(desde, hasta)) {
            selector.setConverter(new ConversorFecha());
            selector.setPrefWidth(130);
            selector.valueProperty().addListener((o, a, n) -> refrescar());
        }
        diasSinVenta.setEditable(true);
        diasSinVenta.setPrefWidth(90);
        diasSinVenta.valueProperty().addListener((o, a, n) -> refrescarSinRotacion());

        graficoDiario = crearGraficoDiario();
        graficoTop = crearGraficoTop();
        construirTablas();

        TabPane subpestanas = new TabPane(
                pestana("Ventas", contenidoVentas()),
                pestana("Sin rotación", contenidoSinRotacion()),
                pestana("Compras por proveedor", contenidoCompras()));
        subpestanas.getStyleClass().add("subpestanas");

        vista.setTop(barraPeriodo());
        vista.setCenter(subpestanas);
        vista.setPadding(new Insets(16));
    }

    @Override
    public String titulo() {
        return "Reportes";
    }

    @Override
    public Node vista() {
        return vista;
    }

    @Override
    public void refrescar() {
        LocalDate inicio = desde.getValue();
        LocalDate fin = hasta.getValue();
        if (inicio == null || fin == null || inicio.isAfter(fin)) {
            return;
        }
        ultimoReporte = servicio.ventas(inicio, fin);
        ingresos.setText(Formatos.moneda(ultimoReporte.ingresos()));
        costo.setText(Formatos.moneda(ultimoReporte.costo()));
        ganancia.setText(Formatos.moneda(ultimoReporte.ganancia()));
        margen.setText(ultimoReporte.margenPorcentaje() + " %");
        tablaVentas.getItems().setAll(ultimoReporte.lineas());
        llenarGraficoDiario(servicio.ventasPorDia(inicio, fin));
        llenarGraficoTop(servicio.masVendidos(inicio, fin, 8));
        tablaCompras.getItems().setAll(servicio.comprasPorProveedor(inicio, fin));
        refrescarSinRotacion();
    }

    // ---- Construcción ----

    private Node barraPeriodo() {
        Label etiqueta = new Label("Período");
        etiqueta.getStyleClass().add("texto-destacado");
        Button hoy = atajo("Hoy", 0);
        Button semana = atajo("7 días", 6);
        Button mes = atajo("30 días", 29);
        Button trimestre = atajo("90 días", 89);
        Button exportar = new Button("Exportar ventas CSV");
        exportar.setOnAction(e -> exportarVentas());
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(8, etiqueta, desde, new Label("a"), hasta, hoy, semana, mes, trimestre, espacio,
                exportar);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.setPadding(new Insets(0, 0, 12, 0));
        return barra;
    }

    private Button atajo(String texto, int diasAtras) {
        Button boton = new Button(texto);
        boton.setOnAction(e -> {
            LocalDate hoy = LocalDate.now();
            hasta.setValue(hoy);
            desde.setValue(hoy.minusDays(diasAtras));
        });
        return boton;
    }

    private Node contenidoVentas() {
        HBox tarjetas = new HBox(16, Componentes.tarjeta("Ventas", ingresos),
                Componentes.tarjeta("Costo de lo vendido", costo), Componentes.tarjeta("Ganancia bruta", ganancia),
                Componentes.tarjeta("Margen", margen));
        tarjetas.getChildren().forEach(t -> HBox.setHgrow(t, Priority.ALWAYS));

        VBox panelDiario = Componentes.panel("Ventas y ganancia por día (" + Formatos.MONEDA + ")",
                graficoDiario);
        VBox panelTop = Componentes.panel("Más vendidos (unidades)", graficoTop);
        HBox.setHgrow(panelDiario, Priority.ALWAYS);
        panelDiario.setPrefWidth(640);
        panelTop.setPrefWidth(380);
        HBox graficos = new HBox(16, panelDiario, panelTop);
        graficos.setPrefHeight(300);
        graficos.setMinHeight(260);

        VBox panelTabla = Componentes.panel("Ventas por producto", tablaVentas);
        tablaVentas.setPrefHeight(300);
        tablaVentas.setMinHeight(200);

        VBox contenido = new VBox(16, tarjetas, graficos, panelTabla);
        contenido.setPadding(new Insets(16, 0, 0, 0));
        ScrollPane desplazable = new ScrollPane(contenido);
        desplazable.setFitToWidth(true);
        desplazable.getStyleClass().add("sin-borde");
        return desplazable;
    }

    private Node contenidoSinRotacion() {
        Label explicacion = new Label("Productos con stock que no se venden desde hace al menos");
        HBox barra = new HBox(8, explicacion, diasSinVenta, new Label("días. Mercadería inmovilizada:"));
        barra.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(tablaSinRotacion, Priority.ALWAYS);
        VBox contenido = new VBox(12, barra, tablaSinRotacion);
        contenido.setPadding(new Insets(16, 0, 0, 0));
        return contenido;
    }

    private Node contenidoCompras() {
        Label explicacion = new Label("Compras registradas en el período, agrupadas por proveedor.");
        explicacion.getStyleClass().add("texto-secundario");
        VBox.setVgrow(tablaCompras, Priority.ALWAYS);
        VBox contenido = new VBox(12, explicacion, tablaCompras);
        contenido.setPadding(new Insets(16, 0, 0, 0));
        return contenido;
    }

    private void construirTablas() {
        tablaVentas.getColumns().add(Tablas.texto("Código", LineaVenta::codigo, 90));
        tablaVentas.getColumns().add(Tablas.texto("Producto", LineaVenta::nombre, 220));
        tablaVentas.getColumns().add(Tablas.numero("Unidades", LineaVenta::unidades, Formatos::entero, 80));
        tablaVentas.getColumns().add(Tablas.numero("Ventas", LineaVenta::ingresos, Formatos::numero, 100));
        tablaVentas.getColumns().add(Tablas.numero("Costo", LineaVenta::costo, Formatos::numero, 100));
        tablaVentas.getColumns().add(Tablas.numero("Ganancia", LineaVenta::ganancia, Formatos::numero, 100));
        tablaVentas.getColumns().add(Tablas.numero("Margen", LineaVenta::margenPorcentaje, m -> m + " %", 80));

        tablaSinRotacion.getColumns().add(Tablas.texto("Código", q -> q.producto().getCodigo(), 90));
        tablaSinRotacion.getColumns().add(Tablas.texto("Producto", q -> q.producto().getNombre(), 220));
        tablaSinRotacion.getColumns().add(Tablas.numero("Stock", q -> q.producto().getStock(), Formatos::entero, 70));
        tablaSinRotacion.getColumns().add(Tablas.numero("Valor al costo", q -> q.producto().costoEnStock(),
                Formatos::numero, 110));
        tablaSinRotacion.getColumns().add(Tablas.texto("Última venta",
                q -> q.ultimaVenta() == null ? "Nunca" : Formatos.fecha(q.ultimaVenta()), 110));
        tablaSinRotacion.getColumns().add(Tablas.texto("Días sin vender",
                q -> q.ultimaVenta() == null ? "—" : String.valueOf(servicio.diasDesde(q.ultimaVenta())), 110));

        tablaCompras.getColumns().add(Tablas.texto("Proveedor", LineaCompra::proveedor, 240));
        tablaCompras.getColumns().add(Tablas.numero("Compras", LineaCompra::compras, Formatos::entero, 90));
        tablaCompras.getColumns().add(Tablas.numero("Unidades", LineaCompra::unidades, Formatos::entero, 90));
        tablaCompras.getColumns().add(Tablas.numero("Monto", LineaCompra::monto, Formatos::numero, 120));
    }

    // ---- Gráficos ----

    private static LineChart<Number, Number> crearGraficoDiario() {
        NumberAxis ejeX = new NumberAxis();
        ejeX.setForceZeroInRange(false);
        ejeX.setMinorTickVisible(false);
        ejeX.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number dia) {
                return LocalDate.ofEpochDay(dia.longValue()).format(DIA_MES);
            }

            @Override
            public Number fromString(String texto) {
                return 0;
            }
        });
        NumberAxis ejeY = new NumberAxis();
        ejeY.setMinorTickVisible(false);
        LineChart<Number, Number> grafico = new LineChart<>(ejeX, ejeY);
        grafico.setAnimated(false);
        grafico.setCreateSymbols(true);
        grafico.setVerticalGridLinesVisible(false);
        grafico.getStyleClass().add("grafico");
        return grafico;
    }

    private static BarChart<Number, String> crearGraficoTop() {
        NumberAxis ejeX = new NumberAxis();
        ejeX.setMinorTickVisible(false);
        CategoryAxis ejeY = new CategoryAxis();
        BarChart<Number, String> grafico = new BarChart<>(ejeX, ejeY);
        grafico.setAnimated(false);
        grafico.setLegendVisible(false);
        grafico.setHorizontalGridLinesVisible(false);
        grafico.setCategoryGap(6);
        grafico.getStyleClass().add("grafico");
        return grafico;
    }

    private void llenarGraficoDiario(List<VentaDiaria> dias) {
        XYChart.Series<Number, Number> serieIngresos = new XYChart.Series<>();
        serieIngresos.setName("Ventas");
        XYChart.Series<Number, Number> serieGanancia = new XYChart.Series<>();
        serieGanancia.setName("Ganancia");
        for (VentaDiaria d : dias) {
            serieIngresos.getData().add(new XYChart.Data<>(d.fecha().toEpochDay(), d.ingresos()));
            serieGanancia.getData().add(new XYChart.Data<>(d.fecha().toEpochDay(), d.ganancia()));
        }
        graficoDiario.getData().setAll(List.of(serieIngresos, serieGanancia));
        // Con pocos días se ve cada fecha; con muchos, el eje elige el espaciado.
        NumberAxis ejeX = (NumberAxis) graficoDiario.getXAxis();
        ejeX.setAutoRanging(false);
        ejeX.setLowerBound(dias.getFirst().fecha().toEpochDay());
        ejeX.setUpperBound(dias.getLast().fecha().toEpochDay());
        ejeX.setTickUnit(Math.max(1, Math.ceil(dias.size() / 8.0)));
        instalarTooltips(serieIngresos, dias, "Ventas");
        instalarTooltips(serieGanancia, dias, "Ganancia");
    }

    private static void instalarTooltips(XYChart.Series<Number, Number> serie, List<VentaDiaria> dias,
                                         String nombre) {
        for (int i = 0; i < serie.getData().size(); i++) {
            XYChart.Data<Number, Number> punto = serie.getData().get(i);
            if (punto.getNode() != null) {
                Tooltip.install(punto.getNode(), new Tooltip(Formatos.fecha(dias.get(i).fecha()) + "\n"
                        + nombre + ": " + Formatos.moneda((BigDecimal) punto.getYValue())));
            }
        }
    }

    private void llenarGraficoTop(List<LineaVenta> top) {
        XYChart.Series<Number, String> serie = new XYChart.Series<>();
        // El más vendido arriba: el eje de categorías dibuja de abajo hacia arriba.
        for (LineaVenta l : top.reversed()) {
            serie.getData().add(new XYChart.Data<>(l.unidades(), recortar(l.nombre())));
        }
        graficoTop.getData().setAll(List.of(serie));
        for (XYChart.Data<Number, String> barra : serie.getData()) {
            if (barra.getNode() != null) {
                Tooltip.install(barra.getNode(), new Tooltip(barra.getYValue() + ": "
                        + Formatos.entero(barra.getXValue()) + " unidades"));
            }
        }
    }

    private void refrescarSinRotacion() {
        try {
            tablaSinRotacion.getItems().setAll(servicio.sinRotacion(diasSinVenta.getValue()));
        } catch (InventarioException e) {
            tablaSinRotacion.getItems().clear();
        }
    }

    // ---- Exportación ----

    private void exportarVentas() {
        if (ultimoReporte == null) {
            return;
        }
        FileChooser selector = new FileChooser();
        selector.setTitle("Exportar ventas");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivos CSV", "*.csv"));
        selector.setInitialFileName("ventas-" + ultimoReporte.desde() + "-a-" + ultimoReporte.hasta() + ".csv");
        File archivo = selector.showSaveDialog(ventana());
        if (archivo == null) {
            return;
        }
        try {
            servicio.exportarVentas(ultimoReporte, archivo.toPath());
            Dialogos.informacion(ventana(), "Exportación terminada", "Reporte guardado en:\n" + archivo);
        } catch (InventarioException e) {
            Dialogos.error(ventana(), e.getMessage());
        }
    }

    // ---- Auxiliares ----

    private Window ventana() {
        return vista.getScene().getWindow();
    }

    private static Tab pestana(String titulo, Node contenido) {
        Tab tab = new Tab(titulo, contenido);
        tab.setClosable(false);
        return tab;
    }

    private static String recortar(String texto) {
        return texto.length() <= 22 ? texto : texto.substring(0, 21) + "…";
    }



    /** Muestra y lee fechas como dd/MM/yyyy. */
    private static final class ConversorFecha extends StringConverter<LocalDate> {
        private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        @Override
        public String toString(LocalDate fecha) {
            return fecha == null ? "" : fecha.format(FORMATO);
        }

        @Override
        public LocalDate fromString(String texto) {
            try {
                return texto == null || texto.isBlank() ? null : LocalDate.parse(texto.strip(), FORMATO);
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }
}
