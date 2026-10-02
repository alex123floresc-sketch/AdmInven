package inventario.persistencia.sqlite;

import inventario.modelo.Movimiento;
import inventario.modelo.Producto;
import inventario.modelo.TipoMovimiento;
import inventario.persistencia.MovimientoRepositorio;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SqliteMovimientoRepositorio implements MovimientoRepositorio {

    private static final String COLUMNAS = "fecha, codigo_producto, tipo, cantidad, stock_resultante, nota, "
            + "precio_unitario, costo_unitario, proveedor_id, usuario";

    private final BaseDeDatos bd;

    public SqliteMovimientoRepositorio(BaseDeDatos bd) {
        this.bd = bd;
    }

    @Override
    public void registrar(Movimiento m) {
        Jdbc.insertar(bd.conexion(),
                "INSERT INTO movimiento (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                m.fecha(), m.codigoProducto(), m.tipo(), m.cantidad(), m.stockResultante(), m.nota(),
                m.precioUnitario(), m.costoUnitario(), m.proveedorId(), m.usuario());
    }

    @Override
    public List<Movimiento> listar() {
        return Jdbc.consultar(bd.conexion(), "SELECT " + COLUMNAS + " FROM movimiento ORDER BY id",
                SqliteMovimientoRepositorio::mapear);
    }

    @Override
    public List<Movimiento> listarPorProducto(String codigoProducto) {
        return Jdbc.consultar(bd.conexion(),
                "SELECT " + COLUMNAS + " FROM movimiento WHERE codigo_producto = ? ORDER BY id",
                SqliteMovimientoRepositorio::mapear, Producto.normalizarCodigo(codigoProducto));
    }

    /** Las fechas se guardan como texto ISO: el rango [desde, hasta + 1 día) se compara como cadenas. */
    @Override
    public List<Movimiento> listarEntre(LocalDate desde, LocalDate hasta) {
        return Jdbc.consultar(bd.conexion(),
                "SELECT " + COLUMNAS + " FROM movimiento WHERE fecha >= ? AND fecha < ? ORDER BY id",
                SqliteMovimientoRepositorio::mapear, desde, hasta.plusDays(1));
    }

    @Override
    public Map<String, LocalDate> ultimaVentaPorProducto() {
        Map<String, LocalDate> ultima = new HashMap<>();
        Jdbc.consultar(bd.conexion(),
                "SELECT codigo_producto, MAX(fecha) AS ultima FROM movimiento WHERE tipo = ? GROUP BY codigo_producto",
                rs -> ultima.put(rs.getString("codigo_producto"), Jdbc.fecha(rs.getString("ultima")).toLocalDate()),
                TipoMovimiento.SALIDA);
        return ultima;
    }

    private static Movimiento mapear(ResultSet rs) throws SQLException {
        return new Movimiento(Jdbc.fecha(rs.getString("fecha")), rs.getString("codigo_producto"),
                TipoMovimiento.valueOf(rs.getString("tipo")), rs.getInt("cantidad"),
                rs.getInt("stock_resultante"), rs.getString("nota"),
                new BigDecimal(rs.getString("precio_unitario")), new BigDecimal(rs.getString("costo_unitario")),
                Jdbc.enteroONulo(rs, "proveedor_id"), rs.getString("usuario"));
    }
}
