package inventario.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class Producto {

    private final String codigo;
    private String nombre;
    private String categoria;
    private BigDecimal precio;
    private int stock;
    private int stockMinimo;
    /** Un producto dado de baja conserva su historial, pero no admite movimientos ni aparece en el catálogo. */
    private boolean activo = true;

    public Producto(String codigo, String nombre, String categoria,
                    BigDecimal precio, int stock, int stockMinimo) {
        this.codigo = normalizarCodigo(codigo);
        setNombre(nombre);
        setCategoria(categoria);
        setPrecio(precio);
        setStock(stock);
        setStockMinimo(stockMinimo);
    }

    /** Los códigos se comparan sin distinguir mayúsculas ni espacios laterales. */
    public static String normalizarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("El código no puede estar vacío.");
        }
        return codigo.strip().toUpperCase(Locale.ROOT);
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre no puede estar vacío.");
        }
        this.nombre = nombre.strip();
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria == null || categoria.isBlank() ? "General" : categoria.strip();
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public void setPrecio(BigDecimal precio) {
        if (precio == null || precio.signum() < 0) {
            throw new IllegalArgumentException("El precio no puede ser negativo.");
        }
        this.precio = precio.setScale(2, RoundingMode.HALF_UP);
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        if (stock < 0) {
            throw new IllegalArgumentException("El stock no puede ser negativo.");
        }
        this.stock = stock;
    }

    public int getStockMinimo() {
        return stockMinimo;
    }

    public void setStockMinimo(int stockMinimo) {
        if (stockMinimo < 0) {
            throw new IllegalArgumentException("El stock mínimo no puede ser negativo.");
        }
        this.stockMinimo = stockMinimo;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public boolean tieneStockBajo() {
        return stock <= stockMinimo;
    }

    public BigDecimal valorEnStock() {
        return precio.multiply(BigDecimal.valueOf(stock));
    }
}
