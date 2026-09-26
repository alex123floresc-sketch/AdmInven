package inventario.ui.fx;

import inventario.modelo.Producto;
import inventario.modelo.Proveedor;
import inventario.modelo.TipoMovimiento;
import inventario.servicio.InventarioServicio;
import inventario.servicio.ProveedorServicio;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.util.Optional;

/** Formulario para registrar una entrada (compra), una salida (venta) o un ajuste de stock. */
final class MovimientoDialogo {

    /** Opción "ninguno" del selector de proveedor. */
    private static final Proveedor SIN_PROVEEDOR = new Proveedor(null, "(Sin proveedor)", "", "", "", true);

    private MovimientoDialogo() {
    }

    static Optional<Producto> mostrar(Window duenio, InventarioServicio servicio, ProveedorServicio proveedores,
                                      Producto producto, TipoMovimiento tipo) {
        Label info = new Label(producto.getNombre() + " (" + producto.getCodigo() + ")");
        info.getStyleClass().add("texto-destacado");
        Label stockActual = new Label(Formatos.entero(producto.getStock()) + " unidades");
        TextField cantidad = new TextField();
        cantidad.setPromptText(tipo == TipoMovimiento.AJUSTE ? "Unidades contadas" : "Unidades");
        TextField nota = new TextField();
        nota.setPromptText(switch (tipo) {
            case ENTRADA -> "Ej. Factura 001";
            case SALIDA -> "Ej. Venta, boleta 123";
            case AJUSTE -> "Motivo del ajuste";
        });

        GridPane rejilla = Dialogos.rejilla();
        Dialogos.fila(rejilla, "Producto", info);
        Dialogos.fila(rejilla, "Stock actual", stockActual);
        Dialogos.fila(rejilla, tipo == TipoMovimiento.AJUSTE ? "Stock contado" : "Cantidad", cantidad);

        ComboBox<Proveedor> proveedor = new ComboBox<>();
        TextField costo = new TextField(producto.getCosto().toPlainString());
        if (tipo == TipoMovimiento.ENTRADA) {
            proveedor.getItems().add(SIN_PROVEEDOR);
            proveedor.getItems().addAll(proveedores.listarActivos());
            proveedor.setValue(SIN_PROVEEDOR);
            Dialogos.fila(rejilla, "Proveedor", proveedor);
            Dialogos.fila(rejilla, "Costo unitario (" + Formatos.MONEDA + ")", costo);
        } else if (tipo == TipoMovimiento.SALIDA) {
            Dialogos.fila(rejilla, "Precio unitario", new Label(Formatos.moneda(producto.getPrecio())));
        }
        Dialogos.fila(rejilla, "Nota", nota);
        Platform.runLater(cantidad::requestFocus);

        String titulo = switch (tipo) {
            case ENTRADA -> "Registrar compra";
            case SALIDA -> "Registrar venta";
            case AJUSTE -> "Ajustar stock";
        };
        return Dialogos.formulario(duenio, titulo, "Registrar", rejilla, () -> {
            int valor = Formatos.leerEntero(tipo == TipoMovimiento.AJUSTE ? "El stock contado" : "La cantidad",
                    cantidad.getText());
            return switch (tipo) {
                case ENTRADA -> {
                    BigDecimal costoCompra = Formatos.leerDecimal("El costo unitario", costo.getText());
                    Long idProveedor = proveedor.getValue() == null ? null : proveedor.getValue().id();
                    yield servicio.registrarEntrada(producto.getCodigo(), valor, costoCompra, idProveedor,
                            nota.getText());
                }
                case SALIDA -> servicio.registrarSalida(producto.getCodigo(), valor, nota.getText());
                case AJUSTE -> servicio.ajustarStock(producto.getCodigo(), valor, nota.getText());
            };
        });
    }
}
