package inventario.persistencia;

import inventario.modelo.Movimiento;
import inventario.modelo.Producto;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Copia los datos de la versión con archivos CSV (productos.csv y movimientos.csv) a otro almacenamiento,
 * normalmente la base de datos. Todo se hace en una transacción: o se importa completo o nada.
 */
public final class ImportadorCsv {

    public record Resultado(int productos, int movimientos, List<String> advertencias) {
    }

    private ImportadorCsv() {
    }

    public static Resultado importar(Path carpetaCsv, ProductoRepositorio destinoProductos,
                                     MovimientoRepositorio destinoMovimientos, Transacciones transacciones) {
        ArchivoProductoRepositorio origenProductos =
                new ArchivoProductoRepositorio(carpetaCsv.resolve("productos.csv"));
        ArchivoMovimientoRepositorio origenMovimientos =
                new ArchivoMovimientoRepositorio(carpetaCsv.resolve("movimientos.csv"));

        List<String> advertencias = new ArrayList<>(origenProductos.getAdvertencias());
        advertencias.addAll(origenMovimientos.getAdvertencias());

        return transacciones.ejecutar(() -> {
            Set<String> codigos = new HashSet<>();
            for (Producto p : origenProductos.listar()) {
                destinoProductos.guardar(p);
                codigos.add(p.getCodigo());
            }
            int importados = 0;
            for (Movimiento m : origenMovimientos.listar()) {
                // Versiones antiguas borraban productos; sus movimientos ya no tienen a qué referirse.
                if (!codigos.contains(m.codigoProducto())) {
                    advertencias.add("Movimiento del " + m.fecha() + " omitido: el producto "
                            + m.codigoProducto() + " no existe.");
                    continue;
                }
                destinoMovimientos.registrar(m);
                importados++;
            }
            return new Resultado(codigos.size(), importados, List.copyOf(advertencias));
        });
    }
}
