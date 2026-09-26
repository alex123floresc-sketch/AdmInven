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
    private final List<String> advertencias = new ArrayList<>();

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

    /** Avisos sobre líneas del archivo que no se pudieron cargar (siguen en el archivo, que nunca se reescribe). */
    public List<String> getAdvertencias() {
        return List.copyOf(advertencias);
    }

    private void cargar() {
        Archivos.leerRegistros(archivo, 6, c -> movimientos.add(new Movimiento(LocalDateTime.parse(c.get(0)),
                c.get(1), leerTipo(c.get(2)), Integer.parseInt(c.get(3)), Integer.parseInt(c.get(4)), c.get(5))),
                advertencias);
    }

    private static TipoMovimiento leerTipo(String texto) {
        try {
            return TipoMovimiento.valueOf(texto);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("tipo de movimiento desconocido \"" + texto + "\"");
        }
    }
}
