package inventario.ui.web;

import inventario.Aplicacion;
import inventario.modelo.Movimiento;
import inventario.modelo.Permiso;
import inventario.modelo.Producto;
import inventario.modelo.TipoMovimiento;
import inventario.modelo.Usuario;
import inventario.servicio.InventarioException;
import inventario.servicio.InventarioServicio;
import inventario.servicio.ReporteServicio;
import inventario.servicio.Sesion;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/** Rutas de la API JSON. Solo traducen HTTP ⇄ servicios: las reglas y los permisos están en los servicios. */
final class Rutas {

    private static final int DIAS_REPORTE_POR_OMISION = 30;

    private final ServidorWeb servidor;

    Rutas(ServidorWeb servidor) {
        this.servidor = servidor;
    }

    void registrar(RoutesConfig r) {
        // Acceso
        r.get("/api/estado", publica(this::estado));
        r.post("/api/sesion", publica(this::iniciarSesion));
        r.delete("/api/sesion", publica(ctx -> {
            servidor.cerrarSesion(ctx);
            ctx.status(HttpStatus.NO_CONTENT);
        }));
        r.post("/api/configuracion-inicial", publica(this::configuracionInicial));
        r.post("/api/sesion/contrasena", privada((ctx, s) -> {
            Json.CambioContrasena datos = ctx.bodyAsClass(Json.CambioContrasena.class);
            s.usuarios().cambiarMiContrasena(clave(datos.actual()), clave(datos.nueva()));
            ctx.status(HttpStatus.NO_CONTENT);
        }));

        // Inventario
        r.get("/api/resumen", privada(this::resumen));
        r.get("/api/productos", privada((ctx, s) -> {
            boolean bajas = "1".equals(ctx.queryParam("bajas"));
            List<Producto> lista = bajas ? s.inventario().listarProductosDadosDeBaja() : s.inventario().listarProductos();
            ctx.json(lista.stream().map(p -> Json.ProductoJson.de(p, verCostos(s))).toList());
        }));
        r.post("/api/productos", privada(this::registrarProducto));
        r.put("/api/productos/{codigo}", privada(this::actualizarProducto));
        r.post("/api/productos/{codigo}/baja", privada((ctx, s) -> {
            s.inventario().darDeBajaProducto(ctx.pathParam("codigo"));
            ctx.status(HttpStatus.NO_CONTENT);
        }));
        r.post("/api/productos/{codigo}/reactivar", privada((ctx, s) -> {
            s.inventario().reactivarProducto(ctx.pathParam("codigo"));
            ctx.status(HttpStatus.NO_CONTENT);
        }));
        r.get("/api/productos/{codigo}/historial", privada((ctx, s) -> {
            Producto p = s.inventario().obtener(ctx.pathParam("codigo"));
            ctx.json(movimientosJson(s, s.inventario().historial(p.getCodigo()).reversed()));
        }));
        r.post("/api/productos/{codigo}/movimientos", privada(this::registrarMovimiento));
        r.get("/api/movimientos", privada((ctx, s) ->
                ctx.json(movimientosJson(s, s.inventario().listarMovimientos()))));

        // Proveedores
        r.get("/api/proveedores", privada((ctx, s) -> ctx.json(("1".equals(ctx.queryParam("todos"))
                ? s.proveedores().listarTodos() : s.proveedores().listarActivos())
                .stream().map(Json.ProveedorJson::de).toList())));
        r.post("/api/proveedores", privada((ctx, s) -> {
            Json.DatosProveedor d = ctx.bodyAsClass(Json.DatosProveedor.class);
            ctx.status(HttpStatus.CREATED).json(Json.ProveedorJson.de(
                    s.proveedores().registrar(d.nombre(), d.documento(), d.telefono(), d.email())));
        }));
        r.put("/api/proveedores/{id}", privada((ctx, s) -> {
            Json.DatosProveedor d = ctx.bodyAsClass(Json.DatosProveedor.class);
            ctx.json(Json.ProveedorJson.de(s.proveedores().actualizar(id(ctx), d.nombre(), d.documento(),
                    d.telefono(), d.email())));
        }));
        r.post("/api/proveedores/{id}/baja", privada((ctx, s) -> {
            s.proveedores().darDeBaja(id(ctx));
            ctx.status(HttpStatus.NO_CONTENT);
        }));
        r.post("/api/proveedores/{id}/reactivar", privada((ctx, s) -> {
            s.proveedores().reactivar(id(ctx));
            ctx.status(HttpStatus.NO_CONTENT);
        }));

        // Reportes
        r.get("/api/reportes", privada(this::reportes));
        r.get("/api/reportes/ventas.csv", privada((ctx, s) -> {
            ReporteServicio.ReporteVentas reporte = s.reportes().ventas(fecha(ctx, "desde"), fecha(ctx, "hasta"));
            descargarCsv(ctx, "ventas-" + reporte.desde() + "-a-" + reporte.hasta() + ".csv",
                    archivo -> s.reportes().exportarVentas(reporte, archivo));
        }));
        r.get("/api/inventario.csv", privada((ctx, s) -> descargarCsv(ctx, "inventario-" + LocalDate.now() + ".csv",
                s.reportes()::exportarInventario)));

        // Usuarios
        r.get("/api/usuarios", privada((ctx, s) ->
                ctx.json(s.usuarios().listar().stream().map(Json.UsuarioJson::de).toList())));
        r.post("/api/usuarios", privada((ctx, s) -> {
            Json.DatosUsuario d = ctx.bodyAsClass(Json.DatosUsuario.class);
            ctx.status(HttpStatus.CREATED).json(Json.UsuarioJson.de(
                    s.usuarios().crearUsuario(d.usuario(), d.nombre(), d.rol(), clave(d.contrasena()))));
        }));
        r.put("/api/usuarios/{usuario}/rol", privada((ctx, s) -> {
            Json.CambioRol d = ctx.bodyAsClass(Json.CambioRol.class);
            if (d.rol() == null) {
                throw new InventarioException("Elija un rol.");
            }
            s.usuarios().cambiarRol(ctx.pathParam("usuario"), d.rol());
            ctx.status(HttpStatus.NO_CONTENT);
        }));
        r.post("/api/usuarios/{usuario}/contrasena", privada((ctx, s) -> {
            s.usuarios().restablecerContrasena(ctx.pathParam("usuario"),
                    clave(ctx.bodyAsClass(Json.NuevaContrasena.class).contrasena()));
            ctx.status(HttpStatus.NO_CONTENT);
        }));
        r.post("/api/usuarios/{usuario}/activar", privada((ctx, s) -> {
            s.usuarios().activar(ctx.pathParam("usuario"));
            ctx.status(HttpStatus.NO_CONTENT);
        }));
        r.post("/api/usuarios/{usuario}/desactivar", privada((ctx, s) -> {
            s.usuarios().desactivar(ctx.pathParam("usuario"));
            ctx.status(HttpStatus.NO_CONTENT);
        }));

        // Copia de seguridad
        r.get("/api/respaldo.db", privada((ctx, s) -> descargar(ctx, s.respaldos().nombreSugerido(),
                "application/vnd.sqlite3", ".db", s.respaldos()::crearCopia)));
    }

    // ---- Acceso ----

    private void estado(Context ctx) {
        Map<String, Object> estado = new LinkedHashMap<>();
        Aplicacion app = servidor.aplicacion();
        estado.put("requiereConfiguracion", servidor.serviciosSinSesion().usuarios().requiereConfiguracionInicial());
        estado.put("demo", app.esDemo());
        estado.put("credencialesDemo", app.esDemo() ? Aplicacion.CREDENCIALES_DEMO : null);
        estado.put("usuario", servidor.sesionActual(ctx)
                .filter(s -> s.servicios.usuarios().actualizarSesion())
                .map(s -> usuarioActual(s.servicios.sesion()))
                .orElse(null));
        ctx.json(estado);
    }

    private void iniciarSesion(Context ctx) {
        Json.Acceso datos = ctx.bodyAsClass(Json.Acceso.class);
        servidor.verificarIntentos(ctx, datos.usuario());
        Aplicacion.Servicios s = servidor.serviciosSinSesion();
        try {
            s.usuarios().iniciarSesion(datos.usuario(), clave(datos.contrasena()));
        } catch (InventarioException e) {
            servidor.registrarIntento(ctx, datos.usuario(), false);
            throw e;
        }
        servidor.registrarIntento(ctx, datos.usuario(), true);
        servidor.abrirSesion(ctx, s);
        ctx.json(usuarioActual(s.sesion()));
    }

    private void configuracionInicial(Context ctx) {
        Json.AdministradorInicial d = ctx.bodyAsClass(Json.AdministradorInicial.class);
        Aplicacion.Servicios s = servidor.serviciosSinSesion();
        s.usuarios().crearAdministradorInicial(d.usuario(), d.nombre(), clave(d.contrasena()));
        servidor.abrirSesion(ctx, s);
        ctx.json(usuarioActual(s.sesion()));
    }

    private static Map<String, Object> usuarioActual(Sesion sesion) {
        Usuario u = sesion.usuario().orElseThrow(ServidorWeb.NoAutenticado::new);
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("usuario", u.nombreUsuario());
        datos.put("nombre", u.nombreCompleto());
        datos.put("rol", u.rol().toString());
        datos.put("permisos", Arrays.stream(Permiso.values()).filter(sesion::puede).map(Enum::name).toList());
        return datos;
    }

    // ---- Inventario ----

    private void resumen(Context ctx, Aplicacion.Servicios s) {
        InventarioServicio inv = s.inventario();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("productosActivos", inv.listarProductos().size());
        r.put("unidades", inv.unidadesTotales());
        r.put("stockBajo", inv.productosConStockBajo().stream().map(p -> Json.ProductoJson.de(p, false)).toList());
        if (verCostos(s)) {
            LocalDate hoy = LocalDate.now();
            r.put("inventarioAlCosto", inv.valorInventarioAlCosto());
            r.put("inventarioAPrecioDeVenta", inv.valorTotalInventario());
            r.put("ventasHoy", s.reportes().ventas(hoy, hoy).ingresos());
            r.put("gananciaHoy", s.reportes().ventas(hoy, hoy).ganancia());
        }
        r.put("ultimosMovimientos", movimientosJson(s, inv.ultimosMovimientos(12)));
        ctx.json(r);
    }

    private void registrarProducto(Context ctx, Aplicacion.Servicios s) {
        Json.DatosProducto d = ctx.bodyAsClass(Json.DatosProducto.class);
        Producto p = s.inventario().registrarProducto(d.codigo(), d.nombre(), d.categoria(),
                decimal("El precio", d.precio()), decimalOpcional("El costo", d.costo(), BigDecimal.ZERO),
                enteroOpcional("El stock inicial", d.stock()), enteroOpcional("El stock mínimo", d.stockMinimo()));
        ctx.status(HttpStatus.CREATED).json(Json.ProductoJson.de(p, verCostos(s)));
    }

    private void actualizarProducto(Context ctx, Aplicacion.Servicios s) {
        Json.DatosProducto d = ctx.bodyAsClass(Json.DatosProducto.class);
        String codigo = ctx.pathParam("codigo");
        BigDecimal precio = decimal("El precio", d.precio());
        int minimo = enteroOpcional("El stock mínimo", d.stockMinimo());
        Producto p = vacio(d.costo())
                ? s.inventario().actualizarProducto(codigo, d.nombre(), d.categoria(), precio, minimo)
                : s.inventario().actualizarProducto(codigo, d.nombre(), d.categoria(), precio,
                decimal("El costo", d.costo()), minimo);
        ctx.json(Json.ProductoJson.de(p, verCostos(s)));
    }

    private void registrarMovimiento(Context ctx, Aplicacion.Servicios s) {
        Json.DatosMovimiento d = ctx.bodyAsClass(Json.DatosMovimiento.class);
        String codigo = ctx.pathParam("codigo");
        TipoMovimiento tipo;
        try {
            tipo = TipoMovimiento.valueOf(String.valueOf(d.tipo()).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InventarioException("Tipo de movimiento desconocido: " + d.tipo());
        }
        Producto p = switch (tipo) {
            case ENTRADA -> s.inventario().registrarEntrada(codigo, entero("La cantidad", d.cantidad()),
                    decimalOpcional("El costo unitario", d.costo(), null), d.proveedorId(), d.nota());
            case SALIDA -> s.inventario().registrarSalida(codigo, entero("La cantidad", d.cantidad()), d.nota());
            case AJUSTE -> s.inventario().ajustarStock(codigo, entero("El stock contado", d.cantidad()), d.nota());
        };
        ctx.json(Json.ProductoJson.de(p, verCostos(s)));
    }

    private static List<Json.MovimientoJson> movimientosJson(Aplicacion.Servicios s, List<Movimiento> movimientos) {
        Map<String, String> nombres = new HashMap<>();
        s.inventario().listarProductos().forEach(p -> nombres.put(p.getCodigo(), p.getNombre()));
        s.inventario().listarProductosDadosDeBaja().forEach(p -> nombres.put(p.getCodigo(), p.getNombre() + " (baja)"));
        boolean costos = verCostos(s);
        return movimientos.stream()
                .map(m -> Json.MovimientoJson.de(m, nombres.getOrDefault(m.codigoProducto(), "?"), costos))
                .toList();
    }

    // ---- Reportes ----

    private void reportes(Context ctx, Aplicacion.Servicios s) {
        LocalDate desde = fecha(ctx, "desde");
        LocalDate hasta = fecha(ctx, "hasta");
        int dias = enteroOpcional("Los días sin venta", ctx.queryParam("diasSinVenta"));
        ReporteServicio rep = s.reportes();
        ReporteServicio.ReporteVentas ventas = rep.ventas(desde, hasta);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("desde", desde);
        r.put("hasta", hasta);
        r.put("ingresos", ventas.ingresos());
        r.put("costo", ventas.costo());
        r.put("ganancia", ventas.ganancia());
        r.put("margen", ventas.margenPorcentaje());
        r.put("unidades", ventas.unidades());
        r.put("lineas", ventas.lineas().stream().map(Rutas::lineaVenta).toList());
        r.put("porDia", rep.ventasPorDia(desde, hasta));
        r.put("masVendidos", rep.masVendidos(desde, hasta, 8).stream().map(Rutas::lineaVenta).toList());
        r.put("compras", rep.comprasPorProveedor(desde, hasta));
        r.put("sinRotacion", rep.sinRotacion(dias > 0 ? dias : 30).stream().map(q -> {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("codigo", q.producto().getCodigo());
            f.put("nombre", q.producto().getNombre());
            f.put("stock", q.producto().getStock());
            f.put("costoEnStock", q.producto().costoEnStock());
            f.put("ultimaVenta", q.ultimaVenta());
            f.put("diasSinVenta", q.ultimaVenta() == null ? null : rep.diasDesde(q.ultimaVenta()));
            return f;
        }).toList());
        ctx.json(r);
    }

    private static Map<String, Object> lineaVenta(ReporteServicio.LineaVenta l) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("codigo", l.codigo());
        f.put("nombre", l.nombre());
        f.put("unidades", l.unidades());
        f.put("ingresos", l.ingresos());
        f.put("costo", l.costo());
        f.put("ganancia", l.ganancia());
        f.put("margen", l.margenPorcentaje());
        return f;
    }

    private static void descargarCsv(Context ctx, String nombre, Consumer<Path> exportar) {
        descargar(ctx, nombre, "text/csv; charset=UTF-8", ".csv", exportar);
    }

    /** El servicio escribe el archivo en una ubicación temporal y se envía como descarga. */
    private static void descargar(Context ctx, String nombre, String tipo, String extension, Consumer<Path> exportar) {
        Path temporal = null;
        try {
            temporal = Files.createTempFile("inventario-", extension);
            exportar.accept(temporal);
            ctx.contentType(tipo)
                    .header("Content-Disposition", "attachment; filename=\"" + nombre + "\"")
                    .result(Files.readAllBytes(temporal));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (temporal != null) {
                try {
                    Files.deleteIfExists(temporal);
                } catch (IOException e) {
                    temporal.toFile().deleteOnExit();
                }
            }
        }
    }

    // ---- Auxiliares ----

    /** Ruta accesible sin iniciar sesión. */
    private Handler publica(Handler manejador) {
        return servidor.exclusivo(manejador);
    }

    /** Ruta que exige sesión; recibe los servicios de quien hace la petición. */
    private Handler privada(BiConsumer<Context, Aplicacion.Servicios> manejador) {
        return servidor.exclusivo(ctx -> manejador.accept(ctx, servidor.servicios(ctx)));
    }

    private static boolean verCostos(Aplicacion.Servicios s) {
        return s.sesion().puede(Permiso.VER_REPORTES);
    }

    private static char[] clave(char[] contrasena) {
        return contrasena == null ? new char[0] : contrasena;
    }

    private static long id(Context ctx) {
        return entero("El identificador", ctx.pathParam("id"));
    }

    private static LocalDate fecha(Context ctx, String parametro) {
        String texto = ctx.queryParam(parametro);
        if (vacio(texto)) {
            LocalDate hoy = LocalDate.now();
            return parametro.equals("desde") ? hoy.minusDays(DIAS_REPORTE_POR_OMISION - 1) : hoy;
        }
        try {
            return LocalDate.parse(texto.strip());
        } catch (DateTimeParseException e) {
            throw new InventarioException("Fecha no válida: " + texto + ".");
        }
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static int entero(String campo, String texto) {
        return numero(campo, texto, t -> Integer.parseInt(t.strip()), " debe ser un número entero.");
    }

    private static int enteroOpcional(String campo, String texto) {
        return vacio(texto) ? 0 : entero(campo, texto);
    }

    /** Acepta coma o punto decimal, como la ventana. */
    private static BigDecimal decimal(String campo, String texto) {
        return numero(campo, texto, t -> new BigDecimal(t.strip().replace(',', '.')),
                " debe ser un número, por ejemplo 12.50.");
    }

    private static BigDecimal decimalOpcional(String campo, String texto, BigDecimal omision) {
        return vacio(texto) ? omision : decimal(campo, texto);
    }

    private static <T> T numero(String campo, String texto, Function<String, T> conversion, String error) {
        if (vacio(texto)) {
            throw new InventarioException(campo + " es obligatorio.");
        }
        try {
            return conversion.apply(texto);
        } catch (NumberFormatException e) {
            throw new InventarioException(campo + error);
        }
    }
}
