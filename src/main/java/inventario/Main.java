package inventario;

import inventario.persistencia.ImportadorCsv;
import inventario.persistencia.sqlite.BaseDeDatos;
import inventario.persistencia.sqlite.SqliteMovimientoRepositorio;
import inventario.persistencia.sqlite.SqliteProductoRepositorio;
import inventario.servicio.InventarioServicio;
import inventario.ui.MenuConsola;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Path carpetaDatos = Path.of(args.length > 0 ? args[0] : "data");
        Path archivoBd = carpetaDatos.resolve("inventario.db");
        boolean baseNueva = !Files.exists(archivoBd);

        try (BaseDeDatos bd = BaseDeDatos.abrir(archivoBd)) {
            SqliteProductoRepositorio productos = new SqliteProductoRepositorio(bd);
            SqliteMovimientoRepositorio movimientos = new SqliteMovimientoRepositorio(bd);
            if (baseNueva && Files.exists(carpetaDatos.resolve("productos.csv"))) {
                importarDatosCsv(carpetaDatos, productos, movimientos, bd);
            }

            InventarioServicio servicio = new InventarioServicio(productos, movimientos, bd);

            // Usa la codificación real de la consola para leer bien tildes y eñes en Windows.
            Charset codificacion = Charset.forName(
                    System.getProperty("stdin.encoding", Charset.defaultCharset().name()));
            new MenuConsola(servicio, new Scanner(System.in, codificacion), System.out).iniciar();
        }
    }

    /** Primera ejecución tras pasar de CSV a SQLite: se conservan los datos anteriores. */
    private static void importarDatosCsv(Path carpeta, SqliteProductoRepositorio productos,
                                         SqliteMovimientoRepositorio movimientos, BaseDeDatos bd) {
        ImportadorCsv.Resultado r = ImportadorCsv.importar(carpeta, productos, movimientos, bd);
        System.out.println("Datos importados desde CSV: " + r.productos() + " producto(s) y "
                + r.movimientos() + " movimiento(s).");
        r.advertencias().forEach(a -> System.out.println("! " + a));
    }
}
