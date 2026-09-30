package inventario;

import inventario.persistencia.ImportadorCsv;
import inventario.persistencia.sqlite.BaseDeDatos;
import inventario.persistencia.sqlite.SqliteMovimientoRepositorio;
import inventario.persistencia.sqlite.SqliteProductoRepositorio;
import inventario.persistencia.sqlite.SqliteProveedorRepositorio;
import inventario.persistencia.sqlite.SqliteUsuarioRepositorio;
import inventario.servicio.InventarioServicio;
import inventario.servicio.ProveedorServicio;
import inventario.servicio.ReporteServicio;
import inventario.servicio.RespaldoServicio;
import inventario.servicio.Sesion;
import inventario.servicio.UsuarioServicio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Arma la aplicación: abre la base de datos, importa los CSV antiguos si hace falta y crea los servicios.
 * La usan la interfaz de escritorio, la consola y el servidor web.
 */
public final class Aplicacion implements AutoCloseable {

    /** Usuarios que crea el modo de demostración (se muestran en la pantalla de inicio de sesión). */
    public static final String CREDENCIALES_DEMO = "admin / admin123  ·  vendedor / vendedor123";

    /** Los servicios que ve un usuario; todos comparten su {@link Sesion}. */
    public record Servicios(InventarioServicio inventario, ProveedorServicio proveedores, ReporteServicio reportes,
                            UsuarioServicio usuarios, RespaldoServicio respaldos, Sesion sesion) {
    }

    private final BaseDeDatos bd;
    private final boolean demo;
    private final SqliteProductoRepositorio repoProductos;
    private final SqliteMovimientoRepositorio repoMovimientos;
    private final SqliteProveedorRepositorio repoProveedores;
    private final SqliteUsuarioRepositorio repoUsuarios;
    private final Clock reloj = Clock.systemDefaultZone();
    /** Servicios de la ventana o la consola, donde hay un único usuario a la vez. */
    private final Servicios local;
    private final List<String> avisos = new ArrayList<>();

    private Aplicacion(Path carpetaDatos, boolean demo) {
        this.demo = demo;
        Path archivoBd = carpetaDatos.resolve("inventario.db");
        boolean baseNueva = !Files.exists(archivoBd);
        bd = BaseDeDatos.abrir(archivoBd);
        try {
            repoProductos = new SqliteProductoRepositorio(bd);
            repoMovimientos = new SqliteMovimientoRepositorio(bd);
            repoProveedores = new SqliteProveedorRepositorio(bd);
            repoUsuarios = new SqliteUsuarioRepositorio(bd);
            if (baseNueva && Files.exists(carpetaDatos.resolve("productos.csv"))) {
                // Primera ejecución tras pasar de CSV a SQLite: se conservan los datos anteriores.
                ImportadorCsv.Resultado r = ImportadorCsv.importar(carpetaDatos, repoProductos, repoMovimientos, bd);
                avisos.add("Datos importados desde CSV: " + r.productos() + " producto(s) y "
                        + r.movimientos() + " movimiento(s).");
                avisos.addAll(r.advertencias());
            }
            if (demo && DatosDemo.cargarSiEstaVacia(repoProductos, repoMovimientos, repoProveedores, repoUsuarios,
                    bd)) {
                avisos.add("Se cargaron datos de ejemplo: un minimarket con 60 días de movimientos.");
            }
            local = serviciosPara(Sesion.nueva());
        } catch (RuntimeException e) {
            bd.close();
            throw e;
        }
    }

    public static Aplicacion iniciar(Path carpetaDatos) {
        return new Aplicacion(carpetaDatos, false);
    }

    /** Como {@link #iniciar(Path)}, pero si la base está vacía la llena con datos de ejemplo. */
    public static Aplicacion iniciarDemo(Path carpetaDatos) {
        return new Aplicacion(carpetaDatos, true);
    }

    /** Servicios para una sesión propia; el servidor web crea uno por cada persona conectada. */
    public Servicios serviciosPara(Sesion sesion) {
        return new Servicios(
                new InventarioServicio(repoProductos, repoMovimientos, bd, reloj, sesion),
                new ProveedorServicio(repoProveedores, sesion),
                new ReporteServicio(repoProductos, repoMovimientos, repoProveedores, reloj, sesion),
                new UsuarioServicio(repoUsuarios, sesion),
                new RespaldoServicio(bd, reloj, sesion),
                sesion);
    }

    public boolean esDemo() {
        return demo;
    }

    public Sesion sesion() {
        return local.sesion();
    }

    public InventarioServicio inventario() {
        return local.inventario();
    }

    public ProveedorServicio proveedores() {
        return local.proveedores();
    }

    public ReporteServicio reportes() {
        return local.reportes();
    }

    public UsuarioServicio usuarios() {
        return local.usuarios();
    }

    public RespaldoServicio respaldos() {
        return local.respaldos();
    }

    /** Mensajes del arranque que conviene mostrar al usuario (importación, líneas ignoradas...). */
    public List<String> avisos() {
        return List.copyOf(avisos);
    }

    @Override
    public void close() {
        bd.close();
    }
}
