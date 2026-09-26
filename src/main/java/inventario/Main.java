package inventario;

import inventario.persistencia.ArchivoMovimientoRepositorio;
import inventario.persistencia.ArchivoProductoRepositorio;
import inventario.servicio.InventarioServicio;
import inventario.ui.MenuConsola;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Path carpetaDatos = Path.of(args.length > 0 ? args[0] : "data");

        InventarioServicio servicio = new InventarioServicio(
                new ArchivoProductoRepositorio(carpetaDatos.resolve("productos.csv")),
                new ArchivoMovimientoRepositorio(carpetaDatos.resolve("movimientos.csv")));

        // Usa la codificación real de la consola para leer bien tildes y eñes en Windows.
        Charset codificacion = Charset.forName(
                System.getProperty("stdin.encoding", Charset.defaultCharset().name()));
        new MenuConsola(servicio, new Scanner(System.in, codificacion), System.out).iniciar();
    }
}
