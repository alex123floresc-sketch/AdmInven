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
import inventario.servicio.Sesion;
import inventario.servicio.UsuarioServicio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Arma la aplicación: abre la base de datos, importa los CSV antiguos si hace falta y crea los servicios,
 * todos compartiendo la misma {@link Sesion}. La usan tanto la interfaz gráfica como la consola.
 */
public final class Aplicacion implements AutoCloseable {

    /** Usuarios que crea el modo de demostración (se muestran en la pantalla de inicio de sesión). */
    public static final String CREDENCIALES_DEMO = "admin / admin123  ·  vendedor / vendedor123";

    private final BaseDeDatos bd;
    private final boolean demo;
    private final Sesion sesion = Sesion.nueva();
    private final InventarioServicio inventario;
    private final ProveedorServicio proveedores;
    private final ReporteServicio reportes;
    private final UsuarioServicio usuarios;
    private final List<String> avisos = new ArrayList<>();

    private Aplicacion(Path carpetaDatos, boolean demo) {
        this.demo = demo;
        Path archivoBd = carpetaDatos.resolve("inventario.db");
        boolean baseNueva = !Files.exists(archivoBd);
        bd = BaseDeDatos.abrir(archivoBd);
        try {
            SqliteProductoRepositorio repoProductos = new SqliteProductoRepositorio(bd);
            SqliteMovimientoRepositorio repoMovimientos = new SqliteMovimientoRepositorio(bd);
            SqliteProveedorRepositorio repoProveedores = new SqliteProveedorRepositorio(bd);
            SqliteUsuarioRepositorio repoUsuarios = new SqliteUsuarioRepositorio(bd);
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
            Clock reloj = Clock.systemDefaultZone();
            inventario = new InventarioServicio(repoProductos, repoMovimientos, bd, reloj, sesion);
            proveedores = new ProveedorServicio(repoProveedores, sesion);
            reportes = new ReporteServicio(repoProductos, repoMovimientos, repoProveedores, reloj, sesion);
            usuarios = new UsuarioServicio(repoUsuarios, sesion);
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

    public boolean esDemo() {
        return demo;
    }

    public Sesion sesion() {
        return sesion;
    }

    public InventarioServicio inventario() {
        return inventario;
    }

    public ProveedorServicio proveedores() {
        return proveedores;
    }

    public ReporteServicio reportes() {
        return reportes;
    }

    public UsuarioServicio usuarios() {
        return usuarios;
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
