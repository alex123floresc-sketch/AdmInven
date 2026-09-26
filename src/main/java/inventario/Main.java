package inventario;

import inventario.persistencia.ArchivoMovimientoRepositorio;
import inventario.persistencia.ArchivoProductoRepositorio;
import inventario.servicio.InventarioServicio;
import inventario.ui.MenuConsola;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Path carpetaDatos = Path.of(args.length > 0 ? args[0] : "data");

        ArchivoProductoRepositorio productos = new ArchivoProductoRepositorio(carpetaDatos.resolve("productos.csv"));
        ArchivoMovimientoRepositorio movimientos =
                new ArchivoMovimientoRepositorio(carpetaDatos.resolve("movimientos.csv"));
        mostrarAdvertencias(productos.getAdvertencias());
        mostrarAdvertencias(movimientos.getAdvertencias());

        InventarioServicio servicio = new InventarioServicio(productos, movimientos);

        // Usa la codificación real de la consola para leer bien tildes y eñes en Windows.
        Charset codificacion = Charset.forName(
                System.getProperty("stdin.encoding", Charset.defaultCharset().name()));
        new MenuConsola(servicio, new Scanner(System.in, codificacion), System.out).iniciar();
    }

    private static void mostrarAdvertencias(List<String> advertencias) {
        for (String advertencia : advertencias) {
            System.out.println("! " + advertencia);
        }
    }
}
