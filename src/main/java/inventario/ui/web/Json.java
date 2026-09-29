package inventario.ui.web;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import inventario.modelo.Movimiento;
import inventario.modelo.Producto;
import inventario.modelo.Proveedor;
import inventario.modelo.Rol;
import inventario.modelo.Usuario;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Formato JSON de la API. Se usan registros propios en lugar de las clases del modelo para decidir
 * exactamente qué ve cada rol: a quien no puede ver reportes nunca se le envían costos ni márgenes.
 */
final class Json {

    private Json() {
    }

    static ObjectMapper mapeador() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    // ---- Respuestas ----

    /** {@code costo} y {@code margen} son null si el usuario no puede verlos. */
    record ProductoJson(String codigo, String nombre, String categoria, BigDecimal precio, BigDecimal costo,
                        BigDecimal margen, int stock, int stockMinimo, BigDecimal valorEnStock, boolean stockBajo,
                        boolean activo) {

        static ProductoJson de(Producto p, boolean verCostos) {
            return new ProductoJson(p.getCodigo(), p.getNombre(), p.getCategoria(), p.getPrecio(),
                    verCostos ? p.getCosto() : null, verCostos ? p.margenPorcentaje() : null, p.getStock(),
                    p.getStockMinimo(), p.valorEnStock(), p.isActivo() && p.tieneStockBajo(), p.isActivo());
        }
    }

    /** {@code cantidad} lleva signo: las ventas son negativas, como en la ventana. */
    record MovimientoJson(LocalDateTime fecha, String codigo, String producto, String tipo, int cantidad,
                          int stockResultante, BigDecimal importe, String usuario, String nota) {

        static MovimientoJson de(Movimiento m, String nombreProducto, boolean verCostos) {
            int cantidad = switch (m.tipo()) {
                case SALIDA -> -m.cantidad();
                case ENTRADA, AJUSTE -> m.cantidad();
            };
            BigDecimal importe = switch (m.tipo()) {
                case SALIDA -> m.importeVenta();
                case ENTRADA -> verCostos ? m.importeCosto() : null;
                case AJUSTE -> null;
            };
            return new MovimientoJson(m.fecha(), m.codigoProducto(), nombreProducto, m.tipo().name(), cantidad,
                    m.stockResultante(), importe, m.usuario(), m.nota());
        }
    }

    record ProveedorJson(long id, String nombre, String documento, String telefono, String email, boolean activo) {

        static ProveedorJson de(Proveedor p) {
            return new ProveedorJson(p.id(), p.nombre(), p.documento(), p.telefono(), p.email(), p.activo());
        }
    }

    record UsuarioJson(String usuario, String nombre, String rol, String rolNombre, boolean activo) {

        static UsuarioJson de(Usuario u) {
            return new UsuarioJson(u.nombreUsuario(), u.nombreCompleto(), u.rol().name(), u.rol().toString(),
                    u.activo());
        }
    }

    record MensajeError(String error) {
    }

    // ---- Peticiones ----

    record Acceso(String usuario, char[] contrasena) {
    }

    record AdministradorInicial(String usuario, String nombre, char[] contrasena) {
    }

    record CambioContrasena(char[] actual, char[] nueva) {
    }

    record DatosProducto(String codigo, String nombre, String categoria, String precio, String costo, String stock,
                         String stockMinimo) {
    }

    /** {@code tipo}: ENTRADA, SALIDA o AJUSTE; en un ajuste, {@code cantidad} es el stock contado. */
    record DatosMovimiento(String tipo, String cantidad, String costo, Long proveedorId, String nota) {
    }

    record DatosProveedor(String nombre, String documento, String telefono, String email) {
    }

    record DatosUsuario(String usuario, String nombre, Rol rol, char[] contrasena) {
    }

    record CambioRol(Rol rol) {
    }

    record NuevaContrasena(char[] contrasena) {
    }
}
