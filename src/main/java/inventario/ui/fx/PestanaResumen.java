package inventario.ui.fx;

import inventario.modelo.Movimiento;
import inventario.modelo.Producto;
import inventario.modelo.Permiso;
import inventario.servicio.InventarioServicio;
import inventario.servicio.ReporteServicio;
import inventario.servicio.Sesion;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Vista general: indicadores clave, productos que hay que reponer y actividad reciente. */
final class PestanaResumen implements Seccion {

    private final InventarioServicio servicio;
    private final ReporteServicio reportes;
    private final boolean verFinanzas;
    private final Label productos = new Label();
    private final Label unidades = new Label();
    private final Label valor = new Label();
    private final Label ventasHoy = new Label();
    private final Label stockBajo = new Label();
    private final VBox tarjetaStockBajo;
    private final TableView<Producto> reponer = Tablas.nueva("Todos los productos tienen stock suficiente.");
    private final Map<String, String> nombres = new HashMap<>();
    private final TableView<Movimiento> recientes;
    private final ScrollPane vista;

    PestanaResumen(InventarioServicio servicio, ReporteServicio reportes, Sesion sesion) {
        this.servicio = servicio;
        this.reportes = reportes;
        this.verFinanzas = sesion.puede(Permiso.VER_REPORTES);
        tarjetaStockBajo = Componentes.tarjeta("Con stock bajo", stockBajo);
        HBox tarjetas = new HBox(16, Componentes.tarjeta("Productos activos", productos),
                Componentes.tarjeta("Unidades en stock", unidades));
        // Costos y ventas solo para quien puede ver reportes.
        if (verFinanzas) {
            tarjetas.getChildren().addAll(Componentes.tarjeta("Inventario al costo", valor),
                    Componentes.tarjeta("Ventas de hoy", ventasHoy));
        }
        tarjetas.getChildren().add(tarjetaStockBajo);
        tarjetas.getChildren().forEach(t -> HBox.setHgrow(t, Priority.ALWAYS));

        reponer.getColumns().add(Tablas.texto("Código", Producto::getCodigo, 80));
        reponer.getColumns().add(Tablas.texto("Nombre", Producto::getNombre, 180));
        reponer.getColumns().add(Tablas.numero("Stock", Producto::getStock, Formatos::entero, 60));
        reponer.getColumns().add(Tablas.numero("Mínimo", Producto::getStockMinimo, Formatos::entero, 60));
        recientes = Tablas.movimientos(c -> nombres.getOrDefault(c, "?"), "Aún no hay movimientos.");
        // En el resumen basta con lo esencial; el detalle completo está en la pestaña Movimientos.
        recientes.getColumns().removeIf(c -> List.of("Código", "Usuario", "Nota").contains(c.getText()));

        VBox panelReponer = Componentes.panel("Para reponer", reponer);
        VBox panelRecientes = Componentes.panel("Últimos movimientos", recientes);
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
        if (verFinanzas) {
            valor.setText(Formatos.moneda(servicio.valorInventarioAlCosto()));
            LocalDate hoy = LocalDate.now();
            ventasHoy.setText(Formatos.moneda(reportes.ventas(hoy, hoy).ingresos()));
        }
        stockBajo.setText(Formatos.entero(bajos.size()));
        tarjetaStockBajo.getStyleClass().remove("tarjeta-alerta");
        if (!bajos.isEmpty()) {
            tarjetaStockBajo.getStyleClass().add("tarjeta-alerta");
        }
        reponer.getItems().setAll(bajos);
        recientes.getItems().setAll(servicio.ultimosMovimientos(15));
    }


}
