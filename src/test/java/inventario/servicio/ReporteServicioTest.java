package inventario.servicio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReporteServicioTest {

    /** Reloj que la prueba mueve para registrar movimientos en distintos días. */
    private static final class Reloj extends Clock {
        private Instant ahora;

        void fijar(LocalDateTime fecha) {
            ahora = fecha.toInstant(ZoneOffset.UTC);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private static final LocalDate HOY = LocalDate.of(2026, 9, 25);

    private final Reloj reloj = new Reloj();
    private InventarioServicio inventario;
    private ProveedorServicio proveedores;
    private ReporteServicio reportes;

    @BeforeEach
    void preparar() {
        ProductoRepositorioEnMemoria productos = new ProductoRepositorioEnMemoria();
        MovimientoRepositorioEnMemoria movimientos = new MovimientoRepositorioEnMemoria();
        ProveedorRepositorioEnMemoria repoProveedores = new ProveedorRepositorioEnMemoria();
        inventario = new InventarioServicio(productos, movimientos, reloj);
        proveedores = new ProveedorServicio(repoProveedores);
        reportes = new ReporteServicio(productos, movimientos, repoProveedores, reloj);

        // Arroz: precio 5.00, costo 4.00. Sal: precio 2.00, costo 1.50. Café: nunca se vende.
        reloj.fijar(HOY.minusDays(40).atTime(8, 0));
        inventario.registrarProducto("ARR", "Arroz", "", bd("5.00"), bd("4.00"), 100, 10);
        inventario.registrarProducto("SAL", "Sal", "", bd("2.00"), bd("1.50"), 50, 5);
        inventario.registrarProducto("CAF", "Café", "", bd("20.00"), bd("15.00"), 5, 1);
        vender(HOY.minusDays(35), "SAL", 3);
        vender(HOY.minusDays(2), "ARR", 10);
        vender(HOY.minusDays(1), "ARR", 5);
        vender(HOY.minusDays(1), "SAL", 4);
    }

    private void vender(LocalDate dia, String codigo, int cantidad) {
        reloj.fijar(dia.atTime(12, 0));
        inventario.registrarSalida(codigo, cantidad, "Venta");
        reloj.fijar(HOY.atTime(20, 0));
    }

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    @Test
    void ventasDelPeriodoPorProductoYTotales() {
        ReporteServicio.ReporteVentas r = reportes.ventas(HOY.minusDays(7), HOY);

        assertEquals(List.of("ARR", "SAL"), r.lineas().stream().map(ReporteServicio.LineaVenta::codigo).toList());
        ReporteServicio.LineaVenta arroz = r.lineas().getFirst();
        assertEquals(15, arroz.unidades());
        assertEquals(bd("75.00"), arroz.ingresos());
        assertEquals(bd("60.00"), arroz.costo());
        assertEquals(bd("15.00"), arroz.ganancia());
        assertEquals(bd("20.0"), arroz.margenPorcentaje());
        assertEquals(19, r.unidades());
        assertEquals(bd("83.00"), r.ingresos());
        assertEquals(bd("17.00"), r.ganancia());
    }

    @Test
    void lasFechasDelPeriodoSonInclusivas() {
        assertEquals(10, reportes.ventas(HOY.minusDays(2), HOY.minusDays(2)).unidades());
        assertEquals(3, reportes.ventas(HOY.minusDays(35), HOY.minusDays(35)).unidades());
    }

    @Test
    void periodoInvalidoSeRechaza() {
        assertThrows(InventarioException.class, () -> reportes.ventas(HOY, HOY.minusDays(1)));
    }

    @Test
    void masVendidosOrdenaPorUnidades() {
        List<ReporteServicio.LineaVenta> top = reportes.masVendidos(HOY.minusDays(60), HOY, 1);

        assertEquals(1, top.size());
        assertEquals("ARR", top.getFirst().codigo());
    }

    @Test
    void ventasPorDiaIncluyeLosDiasSinVentas() {
        List<ReporteServicio.VentaDiaria> dias = reportes.ventasPorDia(HOY.minusDays(2), HOY);

        assertEquals(3, dias.size());
        assertEquals(bd("50.00"), dias.get(0).ingresos());
        assertEquals(bd("33.00"), dias.get(1).ingresos());
        assertEquals(bd("0.00"), dias.get(2).ingresos());
        assertEquals(bd("10.00"), dias.get(0).ganancia());
    }

    @Test
    void sinRotacionListaLoQueNoSeVendeEnElPlazoPrimeroLoQueNuncaSeVendio() {
        List<ReporteServicio.ProductoSinRotacion> quietos = reportes.sinRotacion(30);

        assertEquals(List.of("CAF"), quietos.stream().map(q -> q.producto().getCodigo()).toList());
        assertNull(quietos.getFirst().ultimaVenta());

        assertThrows(InventarioException.class, () -> reportes.sinRotacion(0));
    }

    @Test
    void ventaHaceExactamenteNDiasCuentaComoReciente() {
        // Arroz y sal se vendieron ayer: con plazo de 1 día siguen rotando.
        assertEquals(List.of("CAF"), reportes.sinRotacion(1).stream().map(q -> q.producto().getCodigo()).toList());

        reloj.fijar(HOY.plusDays(1).atTime(9, 0));
        List<ReporteServicio.ProductoSinRotacion> manana = reportes.sinRotacion(1);
        assertEquals(List.of("CAF", "ARR", "SAL"), manana.stream().map(q -> q.producto().getCodigo()).toList());
        assertEquals(HOY.minusDays(1), manana.get(1).ultimaVenta());
    }

    @Test
    void sinRotacionIgnoraProductosSinStockODadosDeBaja() {
        inventario.registrarProducto("VAC", "Vacío", "", bd("1"), 0, 0);
        inventario.darDeBajaProducto("CAF");

        assertTrue(reportes.sinRotacion(30).isEmpty());
    }

    @Test
    void comprasAgrupadasPorProveedor() {
        long andina = proveedores.registrar("Andina", "", "", "").id();
        reloj.fijar(HOY.atTime(9, 0));
        inventario.registrarEntrada("ARR", 20, bd("4.10"), andina, "");
        inventario.registrarEntrada("SAL", 10, bd("1.40"), andina, "");
        inventario.registrarEntrada("CAF", 2, bd("15.00"), null, "");

        List<ReporteServicio.LineaCompra> compras = reportes.comprasPorProveedor(HOY, HOY);

        assertEquals(2, compras.size());
        ReporteServicio.LineaCompra primera = compras.getFirst();
        assertEquals("Andina", primera.proveedor());
        assertEquals(2, primera.compras());
        assertEquals(30, primera.unidades());
        assertEquals(bd("96.00"), primera.monto());
        assertEquals("Sin proveedor", compras.get(1).proveedor());
    }

    @Test
    void exportarVentasGeneraCsvParaExcel(@TempDir Path carpeta) throws IOException {
        Path archivo = carpeta.resolve("ventas.csv");

        reportes.exportarVentas(reportes.ventas(HOY.minusDays(7), HOY), archivo);

        List<String> lineas = Files.readAllLines(archivo, StandardCharsets.UTF_8);
        assertEquals("﻿Código;Producto;Unidades;Ingresos;Costo;Ganancia;Margen %", lineas.getFirst());
        assertEquals("ARR;Arroz;15;75.00;60.00;15.00;20.0", lineas.get(1));
        assertEquals("TOTAL;;19;83.00;66.00;17.00;20.5", lineas.getLast());
    }
}
