package inventario.ui.fx;

import inventario.modelo.Movimiento;
import inventario.modelo.TipoMovimiento;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.function.Function;

/** Construcción de tablas y columnas con el estilo de la aplicación. */
final class Tablas {

    private Tablas() {
    }

    static <S> TableView<S> nueva(String textoVacio) {
        TableView<S> tabla = new TableView<>();
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Label vacio = new Label(textoVacio);
        vacio.getStyleClass().add("texto-secundario");
        tabla.setPlaceholder(vacio);
        return tabla;
    }

    static <S> TableColumn<S, String> texto(String titulo, Function<S, String> valor, double ancho) {
        TableColumn<S, String> columna = new TableColumn<>(titulo);
        columna.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(valor.apply(c.getValue())));
        columna.setPrefWidth(ancho);
        return columna;
    }

    /**
     * Columna numérica alineada a la derecha que ordena por el valor real y muestra el texto formateado.
     */
    static <S, N extends Comparable<N>> TableColumn<S, N> numero(String titulo, Function<S, N> valor,
                                                                   Function<N, String> formato, double ancho) {
        TableColumn<S, N> columna = new TableColumn<>(titulo);
        columna.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(valor.apply(c.getValue())));
        columna.setComparator(Comparator.nullsFirst(Comparator.naturalOrder()));
        columna.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(N item, boolean vacio) {
                super.updateItem(item, vacio);
                setText(vacio || item == null ? null : formato.apply(item));
            }
        });
        columna.getStyleClass().add("columna-numero");
        columna.setPrefWidth(ancho);
        return columna;
    }

    /** Tabla de movimientos; {@code nombreProducto} traduce el código a nombre (null para ocultar la columna). */
    static TableView<Movimiento> movimientos(Function<String, String> nombreProducto, String textoVacio) {
        TableView<Movimiento> tabla = nueva(textoVacio);
        TableColumn<Movimiento, String> fecha = texto("Fecha", m -> Formatos.fechaHora(m.fecha()), 135);
        fecha.setMinWidth(125);
        tabla.getColumns().add(fecha);
        if (nombreProducto != null) {
            tabla.getColumns().add(texto("Código", Movimiento::codigoProducto, 90));
            tabla.getColumns().add(texto("Producto", m -> nombreProducto.apply(m.codigoProducto()), 180));
        }
        TableColumn<Movimiento, String> tipo = texto("Tipo", m -> etiquetaTipo(m), 90);
        tipo.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean vacio) {
                super.updateItem(item, vacio);
                getStyleClass().removeAll("tipo-entrada", "tipo-salida", "tipo-ajuste");
                setText(vacio ? null : item);
                if (!vacio && item != null) {
                    getStyleClass().add("tipo-" + item.toLowerCase());
                }
            }
        });
        tabla.getColumns().add(tipo);
        tabla.getColumns().add(numero("Cantidad", Tablas::cantidadConSigno, Tablas::conSigno, 80));
        tabla.getColumns().add(numero("Importe", Tablas::importe, Formatos::numero, 90));
        tabla.getColumns().add(numero("Stock", Movimiento::stockResultante, Formatos::entero, 70));
        tabla.getColumns().add(texto("Usuario", Movimiento::usuario, 90));
        tabla.getColumns().add(texto("Nota", Movimiento::nota, 200));
        return tabla;
    }

    static String etiquetaTipo(Movimiento m) {
        return switch (m.tipo()) {
            case ENTRADA -> "Entrada";
            case SALIDA -> "Salida";
            case AJUSTE -> "Ajuste";
        };
    }

    /** Ventas a precio de venta; compras y ajustes a costo. */
    private static BigDecimal importe(Movimiento m) {
        return m.tipo() == TipoMovimiento.SALIDA ? m.importeVenta() : m.importeCosto();
    }

    /** Las salidas se guardan en positivo; en pantalla se muestran restando. */
    private static Integer cantidadConSigno(Movimiento m) {
        return m.tipo() == TipoMovimiento.SALIDA ? -m.cantidad() : m.cantidad();
    }

    private static String conSigno(Integer cantidad) {
        return cantidad > 0 ? "+" + Formatos.entero(cantidad) : Formatos.entero(cantidad);
    }
}
