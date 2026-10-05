package inventario.ui.fx;

import inventario.servicio.ReporteServicio.LineaCompra;
import inventario.servicio.ReporteServicio.LineaVenta;
import inventario.servicio.ReporteServicio.ReporteVentas;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Window;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Reporte de ventas listo para imprimir: encabezado, totales, ventas por producto y compras por proveedor,
 * repartido en páginas del tamaño del papel. Para obtener un PDF basta con elegir la impresora
 * «Microsoft Print to PDF» (incluida en Windows).
 */
final class InformeImpreso {

    private static final double ALTO_FILA = 17;
    private static final double ALTO_ENCABEZADO = 70;
    private static final Color GRIS = Color.web("#6b7280");

    /** Anchos relativos de las columnas de cada tabla (suman 1). */
    private static final double[] COLUMNAS_VENTAS = {0.12, 0.32, 0.09, 0.13, 0.12, 0.12, 0.10};
    private static final double[] COLUMNAS_COMPRAS = {0.52, 0.16, 0.16, 0.16};
    private static final double[] COLUMNAS_RESUMEN = {0.25, 0.25};

    private enum Estilo { SECCION, CABECERA, DATO, TOTAL, ESPACIO }

    /** Una fila del informe; las celdas numéricas (todas salvo las dos primeras) se alinean a la derecha. */
    private record Fila(Estilo estilo, double[] columnas, List<String> celdas, int columnasDeTexto) {
    }

    private InformeImpreso() {
    }

    /** Muestra el diálogo de impresión y envía el informe. Devuelve false si no se imprimió. */
    static boolean imprimir(Window duenio, ReporteVentas ventas, List<LineaCompra> compras) {
        PrinterJob trabajo = PrinterJob.createPrinterJob();
        if (trabajo == null) {
            throw new IllegalStateException("No hay ninguna impresora instalada en este equipo.");
        }
        if (!trabajo.showPrintDialog(duenio)) {
            return false;
        }
        PageLayout pagina = trabajo.getJobSettings().getPageLayout();
        for (Node p : paginas(ventas, compras, pagina.getPrintableWidth(), pagina.getPrintableHeight())) {
            if (!trabajo.printPage(p)) {
                trabajo.cancelJob();
                return false;
            }
        }
        return trabajo.endJob();
    }

    /** Las páginas del informe para un área imprimible de {@code ancho} × {@code alto} puntos. */
    static List<Node> paginas(ReporteVentas ventas, List<LineaCompra> compras, double ancho, double alto) {
        List<Fila> filas = filas(ventas, compras);
        int porPagina = Math.max(1, (int) ((alto - ALTO_ENCABEZADO) / ALTO_FILA));
        int total = Math.max(1, (filas.size() + porPagina - 1) / porPagina);
        List<Node> paginas = new ArrayList<>();
        for (int n = 0; n < total; n++) {
            VBox pagina = new VBox(encabezado(ventas, ancho, n + 1, total));
            pagina.setPrefSize(ancho, alto);
            pagina.setMaxSize(ancho, alto);
            pagina.setStyle("-fx-background-color: white;");
            for (Fila f : filas.subList(n * porPagina, Math.min(filas.size(), (n + 1) * porPagina))) {
                pagina.getChildren().add(dibujar(f, ancho));
            }
            paginas.add(pagina);
        }
        return paginas;
    }

    private static List<Fila> filas(ReporteVentas v, List<LineaCompra> compras) {
        List<Fila> filas = new ArrayList<>();
        filas.add(new Fila(Estilo.SECCION, COLUMNAS_RESUMEN, List.of("Resumen del período"), 2));
        resumen(filas, "Ingresos", Formatos.moneda(v.ingresos()));
        resumen(filas, "Costo de lo vendido", Formatos.moneda(v.costo()));
        resumen(filas, "Ganancia", Formatos.moneda(v.ganancia()));
        resumen(filas, "Margen", v.margenPorcentaje() + " %");
        resumen(filas, "Unidades vendidas", Formatos.entero(v.unidades()));
        filas.add(new Fila(Estilo.ESPACIO, COLUMNAS_RESUMEN, List.of(), 0));

        filas.add(new Fila(Estilo.SECCION, COLUMNAS_VENTAS, List.of("Ventas por producto"), 2));
        if (v.lineas().isEmpty()) {
            filas.add(new Fila(Estilo.DATO, COLUMNAS_VENTAS, List.of("No hubo ventas en el período."), 2));
        } else {
            filas.add(new Fila(Estilo.CABECERA, COLUMNAS_VENTAS,
                    List.of("Código", "Producto", "Unidades", "Ingresos", "Costo", "Ganancia", "Margen"), 2));
            for (LineaVenta l : v.lineas()) {
                filas.add(new Fila(Estilo.DATO, COLUMNAS_VENTAS, List.of(l.codigo(), l.nombre(),
                        Formatos.entero(l.unidades()), Formatos.moneda(l.ingresos()), Formatos.moneda(l.costo()),
                        Formatos.moneda(l.ganancia()), l.margenPorcentaje() + " %"), 2));
            }
            filas.add(new Fila(Estilo.TOTAL, COLUMNAS_VENTAS, List.of("Total", "", Formatos.entero(v.unidades()),
                    Formatos.moneda(v.ingresos()), Formatos.moneda(v.costo()), Formatos.moneda(v.ganancia()),
                    v.margenPorcentaje() + " %"), 2));
        }
        filas.add(new Fila(Estilo.ESPACIO, COLUMNAS_VENTAS, List.of(), 0));

        filas.add(new Fila(Estilo.SECCION, COLUMNAS_COMPRAS, List.of("Compras por proveedor"), 1));
        if (compras.isEmpty()) {
            filas.add(new Fila(Estilo.DATO, COLUMNAS_COMPRAS, List.of("No hubo compras en el período."), 1));
        } else {
            filas.add(new Fila(Estilo.CABECERA, COLUMNAS_COMPRAS,
                    List.of("Proveedor", "Compras", "Unidades", "Monto"), 1));
            for (LineaCompra c : compras) {
                filas.add(new Fila(Estilo.DATO, COLUMNAS_COMPRAS, List.of(c.proveedor(),
                        Formatos.entero(c.compras()), Formatos.entero(c.unidades()), Formatos.moneda(c.monto())), 1));
            }
        }
        return filas;
    }

    private static void resumen(List<Fila> filas, String concepto, String valor) {
        filas.add(new Fila(Estilo.DATO, COLUMNAS_RESUMEN, List.of(concepto, valor), 1));
    }

    private static Node encabezado(ReporteVentas v, double ancho, int pagina, int total) {
        Label titulo = etiqueta("Reporte de ventas", Font.font("Segoe UI", FontWeight.BOLD, 16), Color.BLACK);
        Label periodo = etiqueta("Del " + Formatos.fecha(v.desde()) + " al " + Formatos.fecha(v.hasta())
                + "  ·  Impreso el " + Formatos.fechaHora(LocalDateTime.now())
                + "  ·  Página " + pagina + " de " + total, Font.font("Segoe UI", 9), GRIS);
        VBox caja = new VBox(2, etiqueta("Administrador de Inventario", Font.font("Segoe UI", 9), GRIS), titulo,
                periodo);
        caja.setPrefSize(ancho, ALTO_ENCABEZADO);
        caja.setMinHeight(ALTO_ENCABEZADO);
        caja.setMaxHeight(ALTO_ENCABEZADO);
        return caja;
    }

    private static Node dibujar(Fila f, double ancho) {
        HBox fila = new HBox();
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.setMinHeight(ALTO_FILA);
        fila.setPrefHeight(ALTO_FILA);
        fila.setMaxHeight(ALTO_FILA);
        FontWeight peso = f.estilo() == Estilo.DATO || f.estilo() == Estilo.ESPACIO
                ? FontWeight.NORMAL : FontWeight.BOLD;
        Font fuente = Font.font("Segoe UI", peso, f.estilo() == Estilo.SECCION ? 11 : 9);
        Color color = f.estilo() == Estilo.CABECERA ? GRIS : Color.BLACK;
        if (f.estilo() == Estilo.SECCION) {
            Label texto = etiqueta(f.celdas().getFirst(), fuente, color);
            texto.setPrefWidth(ancho);
            fila.getChildren().add(texto);
            return fila;
        }
        if (f.estilo() == Estilo.CABECERA || f.estilo() == Estilo.TOTAL) {
            // Línea bajo la cabecera y sobre el total.
            fila.setStyle(f.estilo() == Estilo.CABECERA
                    ? "-fx-border-color: transparent transparent #9ca3af transparent;"
                    : "-fx-border-color: #9ca3af transparent transparent transparent;");
        }
        for (int i = 0; i < f.celdas().size(); i++) {
            Label celda = etiqueta(f.celdas().get(i), fuente, color);
            double anchoCelda = ancho * (f.celdas().size() == 1 ? 1 : f.columnas()[i]);
            celda.setPrefWidth(anchoCelda);
            celda.setMinWidth(anchoCelda);
            celda.setMaxWidth(anchoCelda);
            celda.setAlignment(i < f.columnasDeTexto() ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
            celda.setPadding(new Insets(0, 4, 0, 0));
            fila.getChildren().add(celda);
        }
        return fila;
    }

    private static Label etiqueta(String texto, Font fuente, Color color) {
        Label etiqueta = new Label(texto);
        etiqueta.setFont(fuente);
        etiqueta.setTextFill(color);
        etiqueta.setMinWidth(Region.USE_PREF_SIZE);
        return etiqueta;
    }
}
