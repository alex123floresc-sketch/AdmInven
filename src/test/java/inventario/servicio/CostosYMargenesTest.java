package inventario.servicio;

import inventario.modelo.Movimiento;
import inventario.modelo.Producto;
import inventario.modelo.TipoMovimiento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CostosYMargenesTest {

    private MovimientoRepositorioEnMemoria movimientos;
    private InventarioServicio servicio;

    @BeforeEach
    void preparar() {
        movimientos = new MovimientoRepositorioEnMemoria();
        servicio = new InventarioServicio(new ProductoRepositorioEnMemoria(), movimientos,
                Clock.fixed(Instant.parse("2026-09-25T10:00:00Z"), ZoneOffset.UTC));
    }

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    @Test
    void margenSeCalculaSobreElPrecioDeVenta() {
        Producto p = servicio.registrarProducto("A1", "Arroz", "", bd("5.00"), bd("3.80"), 0, 0);

        assertEquals(bd("1.20"), p.margenUnitario());
        assertEquals(bd("24.0"), p.margenPorcentaje());
    }

    @Test
    void margenDeProductoGratuitoEsCero() {
        Producto p = servicio.registrarProducto("M1", "Muestra", "", bd("0"), bd("1.00"), 0, 0);

        assertEquals(bd("0.0"), p.margenPorcentaje());
    }

    @Test
    void entradaActualizaElCostoConPromedioPonderado() {
        servicio.registrarProducto("A1", "Arroz", "", bd("5.00"), bd("3.00"), 10, 0);

        // 10 u a 3.00 + 30 u a 4.00 = 150 / 40 = 3.75
        Producto p = servicio.registrarEntrada("A1", 30, bd("4.00"), 7L, "Compra");

        assertEquals(bd("3.75"), p.getCosto());
        Movimiento entrada = movimientos.listar().getLast();
        assertEquals(bd("4.00"), entrada.costoUnitario());
        assertEquals(7L, entrada.proveedorId());
        assertEquals(bd("120.00"), entrada.importeCosto());
    }

    @Test
    void entradaSinStockPrevioTomaElCostoDeCompra() {
        servicio.registrarProducto("A1", "Arroz", "", bd("5.00"), bd("3.00"), 0, 0);

        assertEquals(bd("4.20"), servicio.registrarEntrada("A1", 5, bd("4.20"), null, "").getCosto());
    }

    @Test
    void entradaSinCostoUsaElCostoActual() {
        servicio.registrarProducto("A1", "Arroz", "", bd("5.00"), bd("3.00"), 10, 0);

        Producto p = servicio.registrarEntrada("A1", 5, "Compra");

        assertEquals(bd("3.00"), p.getCosto());
        assertNull(movimientos.listar().getLast().proveedorId());
    }

    @Test
    void costoDeCompraNegativoSeRechaza() {
        servicio.registrarProducto("A1", "Arroz", "", bd("5.00"), bd("3.00"), 10, 0);

        assertThrows(InventarioException.class, () -> servicio.registrarEntrada("A1", 5, bd("-1"), null, ""));
    }

    @Test
    void salidaGuardaPrecioYCostoParaCalcularLaGanancia() {
        servicio.registrarProducto("A1", "Arroz", "", bd("5.00"), bd("3.80"), 10, 0);

        servicio.registrarSalida("A1", 4, "Venta");

        Movimiento venta = movimientos.listar().getLast();
        assertEquals(TipoMovimiento.SALIDA, venta.tipo());
        assertEquals(bd("20.00"), venta.importeVenta());
        assertEquals(bd("15.20"), venta.importeCosto());
    }

    @Test
    void ajusteNegativoValoraLaPerdidaAlCosto() {
        servicio.registrarProducto("A1", "Arroz", "", bd("5.00"), bd("3.80"), 10, 0);

        servicio.ajustarStock("A1", 8, "Mermas");

        assertEquals(bd("7.60"), movimientos.listar().getLast().importeCosto());
    }

    @Test
    void valorDelInventarioAlCostoYAPrecioDeVenta() {
        servicio.registrarProducto("A1", "Arroz", "", bd("5.00"), bd("3.80"), 10, 0);
        servicio.registrarProducto("B2", "Sal", "", bd("2.00"), bd("1.00"), 4, 0);

        assertEquals(bd("42.00"), servicio.valorInventarioAlCosto());
        assertEquals(bd("58.00"), servicio.valorTotalInventario());
    }

    @Test
    void actualizarSinIndicarCostoLoConserva() {
        servicio.registrarProducto("A1", "Arroz", "", bd("5.00"), bd("3.80"), 0, 0);

        assertEquals(bd("3.80"), servicio.actualizarProducto("A1", "Arroz", "", bd("5.50"), 0).getCosto());
        assertEquals(bd("4.00"), servicio.actualizarProducto("A1", "Arroz", "", bd("5.50"), bd("4"), 0).getCosto());
    }

    @Test
    void importarProductosRegistraLosValidosYOmiteLosRepetidos() {
        servicio.registrarProducto("A1", "Arroz", "", bd("5.00"), 0, 0);

        InventarioServicio.ResultadoImportacion r = servicio.importarProductos(List.of(
                new Producto("A1", "Arroz otra vez", "", bd("1"), 0, 0),
                new Producto("B2", "Sal", "Abarrotes", bd("2.00"), bd("1.20"), 7, 2)));

        assertEquals(1, r.importados());
        assertEquals(List.of("A1: Ya existe un producto con el código A1."), r.omitidos());
        Producto sal = servicio.obtener("B2");
        assertEquals(7, sal.getStock());
        assertEquals(bd("1.20"), sal.getCosto());
    }
}
