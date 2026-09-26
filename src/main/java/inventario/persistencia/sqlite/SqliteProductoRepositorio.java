package inventario.persistencia.sqlite;

import inventario.modelo.Producto;
import inventario.persistencia.ProductoRepositorio;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class SqliteProductoRepositorio implements ProductoRepositorio {

    private static final String COLUMNAS = "codigo, nombre, categoria, precio, stock, stock_minimo, activo";

    private final BaseDeDatos bd;

    public SqliteProductoRepositorio(BaseDeDatos bd) {
        this.bd = bd;
    }

    @Override
    public List<Producto> listar() {
        return Jdbc.consultar(bd.conexion(), "SELECT " + COLUMNAS + " FROM producto ORDER BY codigo",
                SqliteProductoRepositorio::mapear);
    }

    @Override
    public Optional<Producto> buscarPorCodigo(String codigo) {
        return Jdbc.consultarUno(bd.conexion(), "SELECT " + COLUMNAS + " FROM producto WHERE codigo = ?",
                SqliteProductoRepositorio::mapear, Producto.normalizarCodigo(codigo));
    }

    @Override
    public void guardar(Producto p) {
        Jdbc.actualizar(bd.conexion(), """
                        INSERT INTO producto (%s) VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (codigo) DO UPDATE SET
                            nombre = excluded.nombre, categoria = excluded.categoria, precio = excluded.precio,
                            stock = excluded.stock, stock_minimo = excluded.stock_minimo, activo = excluded.activo
                        """.formatted(COLUMNAS),
                p.getCodigo(), p.getNombre(), p.getCategoria(), p.getPrecio(), p.getStock(), p.getStockMinimo(),
                p.isActivo());
    }

    @Override
    public boolean eliminar(String codigo) {
        return Jdbc.actualizar(bd.conexion(), "DELETE FROM producto WHERE codigo = ?",
                Producto.normalizarCodigo(codigo)) > 0;
    }

    private static Producto mapear(ResultSet rs) throws SQLException {
        Producto p = new Producto(rs.getString("codigo"), rs.getString("nombre"), rs.getString("categoria"),
                new BigDecimal(rs.getString("precio")), rs.getInt("stock"), rs.getInt("stock_minimo"));
        p.setActivo(rs.getInt("activo") == 1);
        return p;
    }
}
