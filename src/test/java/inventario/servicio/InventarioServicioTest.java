package inventario.servicio;

import inventario.modelo.Movimiento;
import inventario.modelo.Producto;
import inventario.modelo.TipoMovimiento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventarioServicioTest {

    private static final Clock RELOJ_FIJO = Clock.fixed(Instant.parse("2026-09-25T10:15:30Z"), ZoneOffset.UTC);

    private ProductoRepositorioEnMemoria productos;
    private MovimientoRepositorioEnMemoria movimientos;
    private InventarioServicio servicio;

    @BeforeEach
    void preparar() {
        productos = new ProductoRepositorioEnMemoria();
        movimientos = new MovimientoRepositorioEnMemoria();
        servicio = new InventarioServicio(productos, movimientos, RELOJ_FIJO);
    }

    private Producto registrarArroz(int stock) {
        return servicio.registrarProducto("arr-01", "Arroz", "Abarrotes", new BigDecimal("4.50"), stock, 5);
    }

    // ---- Registro ----

    @Test
    void registrarProductoNormalizaCodigoYGuardaStockInicialComoEntrada() {
        Producto p = registrarArroz(20);

        assertEquals("ARR-01", p.getCodigo());
        assertEquals(new BigDecimal("4.50"), p.getPrecio());
        assertEquals(List.of(new Movimiento(LocalDateTime.of(2026, 9, 25, 10, 15, 30), "ARR-01",
                TipoMovimiento.ENTRADA, 20, 20, "Stock inicial", BigDecimal.ZERO, BigDecimal.ZERO, null, "sistema")),
                movimientos.listar());
    }

    @Test
    void registrarProductoSinStockNoCreaMovimiento() {
        registrarArroz(0);

        assertTrue(movimientos.listar().isEmpty());
    }

    @Test
    void registrarCodigoDuplicadoFallaSinImportarMayusculas() {
        registrarArroz(10);

        InventarioException e = assertThrows(InventarioException.class,
                () -> servicio.registrarProducto(" ARR-01 ", "Otro", "", BigDecimal.ONE, 0, 0));
        assertEquals("Ya existe un producto con el código ARR-01.", e.getMessage());
    }

    @Test
    void registrarConDatosInvalidosLanzaErrorDeNegocio() {
        assertThrows(InventarioException.class,
                () -> servicio.registrarProducto("", "Arroz", "", BigDecimal.ONE, 0, 0));
        assertThrows(InventarioException.class,
                () -> servicio.registrarProducto("X1", " ", "", BigDecimal.ONE, 0, 0));
        assertThrows(InventarioException.class,
                () -> servicio.registrarProducto("X1", "Arroz", "", new BigDecimal("-1"), 0, 0));
        assertThrows(InventarioException.class,
                () -> servicio.registrarProducto("X1", "Arroz", "", BigDecimal.ONE, -1, 0));
    }

    @Test
    void categoriaVaciaSeGuardaComoGeneral() {
        Producto p = servicio.registrarProducto("X1", "Sal", "  ", BigDecimal.ONE, 0, 0);

        assertEquals("General", p.getCategoria());
    }

    // ---- Edición y eliminación ----

    @Test
    void actualizarProductoCambiaDatosPeroNoElStock() {
        registrarArroz(20);

        Producto p = servicio.actualizarProducto("arr-01", "Arroz extra", "Granos", new BigDecimal("5"), 8);

        assertEquals("Arroz extra", p.getNombre());
        assertEquals("Granos", p.getCategoria());
        assertEquals(new BigDecimal("5.00"), p.getPrecio());
        assertEquals(8, p.getStockMinimo());
        assertEquals(20, p.getStock());
    }

    @Test
    void actualizarConDatoInvalidoNoModificaNada() {
        registrarArroz(20);

        assertThrows(InventarioException.class,
                () -> servicio.actualizarProducto("ARR-01", "Nuevo nombre", "Granos", new BigDecimal("-3"), 8));

        Producto p = servicio.obtener("ARR-01");
        assertEquals("Arroz", p.getNombre());
        assertEquals("Abarrotes", p.getCategoria());
    }

    // ---- Baja lógica ----

    @Test
    void productoDadoDeBajaSaleDelCatalogoYDeLosTotalesPeroConservaSuHistorial() {
        registrarArroz(10);
        servicio.registrarProducto("SAL", "Sal", "", BigDecimal.ONE, 2, 5);
        servicio.registrarSalida("ARR-01", 1, "Venta");

        servicio.darDeBajaProducto("arr-01");

        assertEquals(List.of("SAL"), codigos(servicio.listarProductos()));
        assertEquals(List.of("ARR-01"), codigos(servicio.listarProductosDadosDeBaja()));
        assertTrue(servicio.buscar("arroz").isEmpty());
        assertEquals(List.of("SAL"), codigos(servicio.productosConStockBajo()));
        assertEquals(new BigDecimal("2.00"), servicio.valorTotalInventario());
        assertEquals(2, servicio.unidadesTotales());
        assertEquals(2, servicio.historial("ARR-01").size());
        assertFalse(servicio.obtener("ARR-01").isActivo());
    }

    @Test
    void productoDadoDeBajaNoAdmiteMovimientosNiEdicion() {
        registrarArroz(10);
        servicio.darDeBajaProducto("ARR-01");

        String esperado = "El producto ARR-01 está dado de baja.";
        assertEquals(esperado, assertThrows(InventarioException.class,
                () -> servicio.registrarEntrada("ARR-01", 1, "")).getMessage());
        assertThrows(InventarioException.class, () -> servicio.registrarSalida("ARR-01", 1, ""));
        assertThrows(InventarioException.class, () -> servicio.ajustarStock("ARR-01", 0, ""));
        assertThrows(InventarioException.class,
                () -> servicio.actualizarProducto("ARR-01", "X", "", BigDecimal.ONE, 0));
        assertThrows(InventarioException.class, () -> servicio.darDeBajaProducto("ARR-01"));
        assertEquals(10, servicio.obtener("ARR-01").getStock());
    }

    @Test
    void reactivarDevuelveElProductoAlCatalogo() {
        registrarArroz(10);
        servicio.darDeBajaProducto("ARR-01");

        servicio.reactivarProducto("arr-01");

        assertEquals(List.of("ARR-01"), codigos(servicio.listarProductos()));
        assertEquals(11, servicio.registrarEntrada("ARR-01", 1, "").getStock());
        assertThrows(InventarioException.class, () -> servicio.reactivarProducto("ARR-01"));
    }

    @Test
    void codigoDeProductoDadoDeBajaNoSePuedeReutilizar() {
        registrarArroz(10);
        servicio.darDeBajaProducto("ARR-01");

        InventarioException e = assertThrows(InventarioException.class, () -> registrarArroz(0));
        assertEquals("El código ARR-01 pertenece a un producto dado de baja; reactívelo en su lugar.", e.getMessage());
        assertThrows(InventarioException.class, () -> servicio.validarCodigoDisponible(" "));
    }

    @Test
    void siFallaGuardarLaBajaElProductoSigueActivo() {
        registrarArroz(10);
        productos.fallarAlGuardar = true;

        assertThrows(InventarioException.class, () -> servicio.darDeBajaProducto("ARR-01"));

        assertTrue(servicio.obtener("ARR-01").isActivo());
    }

    private static List<String> codigos(List<Producto> lista) {
        return lista.stream().map(Producto::getCodigo).toList();
    }

    @Test
    void obtenerProductoInexistenteFalla() {
        InventarioException e = assertThrows(InventarioException.class, () -> servicio.obtener("NADA"));
        assertEquals("No existe un producto con el código NADA.", e.getMessage());
        assertThrows(InventarioException.class, () -> servicio.obtener(" "));
    }

    // ---- Movimientos de stock ----

    @Test
    void entradaSumaStockYRegistraMovimiento() {
        registrarArroz(20);

        Producto p = servicio.registrarEntrada("ARR-01", 15, "Compra");

        assertEquals(35, p.getStock());
        Movimiento ultimo = movimientos.listar().getLast();
        assertEquals(TipoMovimiento.ENTRADA, ultimo.tipo());
        assertEquals(15, ultimo.cantidad());
        assertEquals(35, ultimo.stockResultante());
        assertEquals("Compra", ultimo.nota());
    }

    @Test
    void salidaRestaStockYRegistraMovimiento() {
        registrarArroz(20);

        Producto p = servicio.registrarSalida("ARR-01", 18, "Venta");

        assertEquals(2, p.getStock());
        assertEquals(TipoMovimiento.SALIDA, movimientos.listar().getLast().tipo());
    }

    @Test
    void salidaConStockInsuficienteFallaSinCambiarNada() {
        registrarArroz(20);
        int movimientosAntes = movimientos.listar().size();

        InventarioException e = assertThrows(InventarioException.class,
                () -> servicio.registrarSalida("ARR-01", 21, "Venta"));

        assertEquals("Stock insuficiente: hay 20 unidades de Arroz.", e.getMessage());
        assertEquals(20, servicio.obtener("ARR-01").getStock());
        assertEquals(movimientosAntes, movimientos.listar().size());
    }

    @Test
    void salidaDeTodoElStockEsValida() {
        registrarArroz(20);

        assertEquals(0, servicio.registrarSalida("ARR-01", 20, "").getStock());
    }

    @Test
    void cantidadCeroONegativaSeRechaza() {
        registrarArroz(20);

        assertThrows(InventarioException.class, () -> servicio.registrarEntrada("ARR-01", 0, ""));
        assertThrows(InventarioException.class, () -> servicio.registrarSalida("ARR-01", -5, ""));
    }

    @Test
    void ajusteRegistraLaDiferenciaConSigno() {
        registrarArroz(20);

        Producto p = servicio.ajustarStock("ARR-01", 17, "Conteo");

        assertEquals(17, p.getStock());
        Movimiento ultimo = movimientos.listar().getLast();
        assertEquals(TipoMovimiento.AJUSTE, ultimo.tipo());
        assertEquals(-3, ultimo.cantidad());
        assertEquals(17, ultimo.stockResultante());
    }

    @Test
    void ajusteSinDiferenciaNoRegistraMovimiento() {
        registrarArroz(20);
        int movimientosAntes = movimientos.listar().size();

        servicio.ajustarStock("ARR-01", 20, "Conteo");

        assertEquals(movimientosAntes, movimientos.listar().size());
    }

    @Test
    void ajusteNegativoSeRechaza() {
        registrarArroz(20);

        assertThrows(InventarioException.class, () -> servicio.ajustarStock("ARR-01", -1, ""));
    }

    // ---- Coherencia ante fallos de guardado ----

    @Test
    void siFallaElMovimientoLaSalidaNoCambiaElStock() {
        registrarArroz(20);
        movimientos.fallarAlRegistrar = true;

        InventarioException e = assertThrows(InventarioException.class,
                () -> servicio.registrarSalida("ARR-01", 5, "Venta"));

        assertEquals("No se pudo guardar el movimiento; el stock no se modificó.", e.getMessage());
        assertEquals(20, servicio.obtener("ARR-01").getStock());
        assertEquals(20, productos.buscarPorCodigo("ARR-01").orElseThrow().getStock());
    }

    @Test
    void siFallaGuardarElProductoLaEntradaNoCambiaStockNiRegistraMovimiento() {
        registrarArroz(20);
        int movimientosAntes = movimientos.listar().size();
        productos.fallarAlGuardar = true;

        assertThrows(InventarioException.class, () -> servicio.registrarEntrada("ARR-01", 5, ""));

        assertEquals(20, servicio.obtener("ARR-01").getStock());
        assertEquals(movimientosAntes, movimientos.listar().size());
    }

    @Test
    void siFallaElMovimientoElAjusteNoCambiaElStock() {
        registrarArroz(20);
        movimientos.fallarAlRegistrar = true;

        assertThrows(InventarioException.class, () -> servicio.ajustarStock("ARR-01", 3, ""));

        assertEquals(20, servicio.obtener("ARR-01").getStock());
    }

    @Test
    void siFallaElMovimientoInicialElProductoNoQuedaRegistrado() {
        movimientos.fallarAlRegistrar = true;

        assertThrows(InventarioException.class, () -> registrarArroz(20));

        assertTrue(productos.buscarPorCodigo("ARR-01").isEmpty());
    }

    @Test
    void siFallaGuardarLaEdicionElProductoConservaSusDatos() {
        registrarArroz(20);
        productos.fallarAlGuardar = true;

        assertThrows(InventarioException.class,
                () -> servicio.actualizarProducto("ARR-01", "Arroz extra", "Granos", BigDecimal.TEN, 1));

        Producto p = servicio.obtener("ARR-01");
        assertEquals("Arroz", p.getNombre());
        assertEquals(new BigDecimal("4.50"), p.getPrecio());
    }

    // ---- Consultas ----

    @Test
    void stockBajoIncluyeProductosEnOBajoElMinimoOrdenadosPorStock() {
        servicio.registrarProducto("A", "Justo en el mínimo", "", BigDecimal.ONE, 5, 5);
        servicio.registrarProducto("B", "Suficiente", "", BigDecimal.ONE, 6, 5);
        servicio.registrarProducto("C", "Agotado", "", BigDecimal.ONE, 0, 5);

        List<String> codigos = servicio.productosConStockBajo().stream().map(Producto::getCodigo).toList();

        assertEquals(List.of("C", "A"), codigos);
    }

    @Test
    void valorTotalYUnidadesSumanTodoElInventario() {
        servicio.registrarProducto("A", "Arroz", "", new BigDecimal("4.50"), 10, 0);
        servicio.registrarProducto("B", "Azúcar", "", new BigDecimal("3.25"), 4, 0);

        assertEquals(new BigDecimal("58.00"), servicio.valorTotalInventario());
        assertEquals(14, servicio.unidadesTotales());
    }

    @Test
    void inventarioVacioValeCero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(servicio.valorTotalInventario()));
        assertEquals(0, servicio.unidadesTotales());
    }

    @Test
    void buscarCoincideConCodigoNombreOCategoriaSinDistinguirMayusculas() {
        servicio.registrarProducto("LAP-01", "Laptop", "Cómputo", BigDecimal.ONE, 0, 0);
        servicio.registrarProducto("MOU-01", "Mouse", "Periféricos", BigDecimal.ONE, 0, 0);

        assertEquals(1, servicio.buscar("lap").size());
        assertEquals(1, servicio.buscar("MOUSE").size());
        assertEquals(1, servicio.buscar("periF").size());
        assertEquals(2, servicio.buscar("").size());
        assertTrue(servicio.buscar("teclado").isEmpty());
    }

    @Test
    void listarProductosOrdenaPorCodigo() {
        servicio.registrarProducto("C", "Tres", "", BigDecimal.ONE, 0, 0);
        servicio.registrarProducto("A", "Uno", "", BigDecimal.ONE, 0, 0);
        servicio.registrarProducto("B", "Dos", "", BigDecimal.ONE, 0, 0);

        assertEquals(List.of("A", "B", "C"),
                servicio.listarProductos().stream().map(Producto::getCodigo).toList());
    }

    @Test
    void historialSoloIncluyeMovimientosDelProducto() {
        registrarArroz(20);
        servicio.registrarProducto("OTRO", "Otro", "", BigDecimal.ONE, 3, 0);
        servicio.registrarSalida("ARR-01", 2, "");

        List<Movimiento> historial = servicio.historial("arr-01");

        assertEquals(2, historial.size());
        assertTrue(historial.stream().allMatch(m -> m.codigoProducto().equals("ARR-01")));
    }

    @Test
    void ultimosMovimientosDevuelveLosMasRecientesPrimero() {
        registrarArroz(20);
        servicio.registrarSalida("ARR-01", 1, "primera");
        servicio.registrarSalida("ARR-01", 1, "segunda");

        List<Movimiento> ultimos = servicio.ultimosMovimientos(2);

        assertEquals(List.of("segunda", "primera"), ultimos.stream().map(Movimiento::nota).toList());
        assertEquals(3, servicio.ultimosMovimientos(10).size());
    }

    @Test
    void listarMovimientosDevuelveTodosDelMasRecienteAlMasAntiguo() {
        registrarArroz(20);
        servicio.registrarSalida("ARR-01", 1, "venta");

        assertEquals(List.of("venta", "Stock inicial"),
                servicio.listarMovimientos().stream().map(Movimiento::nota).toList());
    }

    @Test
    void listarCategoriasDevuelveLasDeProductosActivosSinRepetir() {
        servicio.registrarProducto("A", "Arroz", "Granos", BigDecimal.ONE, 0, 0);
        servicio.registrarProducto("B", "Frejol", "Granos", BigDecimal.ONE, 0, 0);
        servicio.registrarProducto("C", "Leche", "Lácteos", BigDecimal.ONE, 0, 0);
        servicio.registrarProducto("D", "Jabón", "Limpieza", BigDecimal.ONE, 0, 0);
        servicio.darDeBajaProducto("D");

        assertEquals(List.of("Granos", "Lácteos"), servicio.listarCategorias());
    }

    @Test
    void obtenerDevuelveLaMismaInstanciaGuardada() {
        Producto p = registrarArroz(1);

        assertSame(p, servicio.obtener("arr-01"));
    }
}
