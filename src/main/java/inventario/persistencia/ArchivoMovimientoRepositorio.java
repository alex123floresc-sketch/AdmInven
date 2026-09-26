package inventario.persistencia;

import inventario.modelo.Movimiento;
import inventario.modelo.Producto;
import inventario.modelo.TipoMovimiento;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ArchivoMovimientoRepositorio implements MovimientoRepositorio {

    private static final String CABECERA = "fecha;codigoProducto;tipo;cantidad;stockResultante;nota";

    private final Path archivo;
    private final List<Movimiento> movimientos = new ArrayList<>();

    public ArchivoMovimientoRepositorio(Path archivo) {
        this.archivo = archivo;
        cargar();
    }

    /** Añade solo la línea nueva al archivo, sin reescribir el historial existente. */
    @Override
    public void registrar(Movimiento movimiento) {
        Archivos.agregarLinea(archivo, CABECERA, Csv.unir(movimiento.fecha(), movimiento.codigoProducto(),
                movimiento.tipo(), movimiento.cantidad(), movimiento.stockResultante(), movimiento.nota()));
        movimientos.add(movimiento);
    }

    @Override
    public List<Movimiento> listar() {
        return List.copyOf(movimientos);
    }

    @Override
    public List<Movimiento> listarPorProducto(String codigoProducto) {
        String codigo = Producto.normalizarCodigo(codigoProducto);
        return movimientos.stream()
                .filter(m -> m.codigoProducto().equals(codigo))
                .toList();
    }

    private void cargar() {
        List<String> lineas = Archivos.leerLineas(archivo);
        for (int i = 1; i < lineas.size(); i++) {
            String linea = lineas.get(i);
            if (linea.isBlank()) {
                continue;
            }
            List<String> c = Csv.separar(linea);
            if (c.size() < 6) {
                throw new IllegalStateException("Línea " + (i + 1) + " inválida en " + archivo);
            }
            movimientos.add(new Movimiento(LocalDateTime.parse(c.get(0)), c.get(1),
                    TipoMovimiento.valueOf(c.get(2)), Integer.parseInt(c.get(3)),
                    Integer.parseInt(c.get(4)), c.get(5)));
        }
    }
}
