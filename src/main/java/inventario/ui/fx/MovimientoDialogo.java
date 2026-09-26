package inventario.ui.fx;

import inventario.modelo.Producto;
import inventario.modelo.TipoMovimiento;
import inventario.servicio.InventarioServicio;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

import java.util.Optional;

/** Formulario para registrar una entrada, una salida o un ajuste de stock de un producto. */
final class MovimientoDialogo {

    private MovimientoDialogo() {
    }

    static Optional<Producto> mostrar(Window duenio, InventarioServicio servicio, Producto producto,
                                      TipoMovimiento tipo) {
        Label info = new Label(producto.getNombre() + " (" + producto.getCodigo() + ")");
        info.getStyleClass().add("texto-destacado");
        Label stockActual = new Label(Formatos.entero(producto.getStock()) + " unidades");
        TextField cantidad = new TextField();
        cantidad.setPromptText(tipo == TipoMovimiento.AJUSTE ? "Unidades contadas" : "Unidades");
        TextField nota = new TextField();
        nota.setPromptText(switch (tipo) {
            case ENTRADA -> "Ej. Compra, factura 001";
            case SALIDA -> "Ej. Venta, boleta 123";
            case AJUSTE -> "Motivo del ajuste";
        });

        GridPane rejilla = Dialogos.rejilla();
        Dialogos.fila(rejilla, "Producto", info);
        Dialogos.fila(rejilla, "Stock actual", stockActual);
        Dialogos.fila(rejilla, tipo == TipoMovimiento.AJUSTE ? "Stock contado" : "Cantidad", cantidad);
        Dialogos.fila(rejilla, "Nota", nota);
        Platform.runLater(cantidad::requestFocus);

        String titulo = switch (tipo) {
            case ENTRADA -> "Registrar entrada";
            case SALIDA -> "Registrar salida";
            case AJUSTE -> "Ajustar stock";
        };
        return Dialogos.formulario(duenio, titulo, "Registrar", rejilla, () -> {
            int valor = Formatos.leerEntero(tipo == TipoMovimiento.AJUSTE ? "El stock contado" : "La cantidad",
                    cantidad.getText());
            return switch (tipo) {
                case ENTRADA -> servicio.registrarEntrada(producto.getCodigo(), valor, nota.getText());
                case SALIDA -> servicio.registrarSalida(producto.getCodigo(), valor, nota.getText());
                case AJUSTE -> servicio.ajustarStock(producto.getCodigo(), valor, nota.getText());
            };
        });
    }
}
