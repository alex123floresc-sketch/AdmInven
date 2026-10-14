package inventario.persistencia.sqlite;

import inventario.modelo.EstadoPedido;
import inventario.modelo.Pedido;
import inventario.persistencia.PedidoRepositorio;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Las líneas se insertan junto con el pedido; quien llama debe hacerlo dentro de una transacción. */
public class SqlitePedidoRepositorio implements PedidoRepositorio {

    private static final String COLUMNAS =
            "id, fecha, cliente, telefono, nota, estado, atendido_por, fecha_atencion";

    private final BaseDeDatos bd;

    public SqlitePedidoRepositorio(BaseDeDatos bd) {
        this.bd = bd;
    }

    /** Fila de la tabla pedido, antes de unirle sus líneas. */
    private record Fila(long id, String fecha, String cliente, String telefono, String nota, String estado,
                        String atendidoPor, String fechaAtencion) {
    }

    private record Linea(long pedidoId, Pedido.LineaPedido linea) {
    }

    @Override
    public List<Pedido> listar() {
        List<Fila> filas = Jdbc.consultar(bd.conexion(),
                "SELECT " + COLUMNAS + " FROM pedido ORDER BY fecha DESC, id DESC", SqlitePedidoRepositorio::fila);
        Map<Long, List<Pedido.LineaPedido>> lineas = new HashMap<>();
        Jdbc.consultar(bd.conexion(), "SELECT * FROM pedido_linea ORDER BY pedido_id, orden",
                        SqlitePedidoRepositorio::linea)
                .forEach(l -> lineas.computeIfAbsent(l.pedidoId(), k -> new ArrayList<>()).add(l.linea()));
        return filas.stream().map(f -> armar(f, lineas.getOrDefault(f.id(), List.of()))).toList();
    }

    @Override
    public Optional<Pedido> buscarPorId(long id) {
        return Jdbc.consultarUno(bd.conexion(), "SELECT " + COLUMNAS + " FROM pedido WHERE id = ?",
                SqlitePedidoRepositorio::fila, id).map(f -> armar(f, Jdbc.consultar(bd.conexion(),
                "SELECT * FROM pedido_linea WHERE pedido_id = ? ORDER BY orden", SqlitePedidoRepositorio::linea, id)
                .stream().map(Linea::linea).toList()));
    }

    @Override
    public Pedido guardar(Pedido p) {
        if (p.id() != null) {
            Jdbc.actualizar(bd.conexion(),
                    "UPDATE pedido SET estado = ?, atendido_por = ?, fecha_atencion = ? WHERE id = ?",
                    p.estado(), p.atendidoPor(), p.fechaAtencion(), p.id());
            return p;
        }
        long id = Jdbc.insertar(bd.conexion(), "INSERT INTO pedido (fecha, cliente, telefono, nota, estado, "
                        + "atendido_por, fecha_atencion) VALUES (?, ?, ?, ?, ?, ?, ?)",
                p.fecha(), p.cliente(), p.telefono(), p.nota(), p.estado(), p.atendidoPor(), p.fechaAtencion());
        for (int i = 0; i < p.lineas().size(); i++) {
            Pedido.LineaPedido l = p.lineas().get(i);
            Jdbc.actualizar(bd.conexion(), "INSERT INTO pedido_linea (pedido_id, orden, codigo_producto, nombre, "
                    + "cantidad, precio) VALUES (?, ?, ?, ?, ?, ?)", id, i, l.codigo(), l.nombre(), l.cantidad(), l.precio());
        }
        return p.conId(id);
    }

    private static Pedido armar(Fila f, List<Pedido.LineaPedido> lineas) {
        return new Pedido(f.id(), Jdbc.fecha(f.fecha()), f.cliente(), f.telefono(), f.nota(), lineas,
                EstadoPedido.valueOf(f.estado()), f.atendidoPor(),
                f.fechaAtencion() == null ? null : Jdbc.fecha(f.fechaAtencion()));
    }

    private static Fila fila(ResultSet rs) throws SQLException {
        return new Fila(rs.getLong("id"), rs.getString("fecha"), rs.getString("cliente"), rs.getString("telefono"),
                rs.getString("nota"), rs.getString("estado"), rs.getString("atendido_por"),
                rs.getString("fecha_atencion"));
    }

    private static Linea linea(ResultSet rs) throws SQLException {
        return new Linea(rs.getLong("pedido_id"), new Pedido.LineaPedido(rs.getString("codigo_producto"),
                rs.getString("nombre"), rs.getInt("cantidad"), new BigDecimal(rs.getString("precio"))));
    }
}
