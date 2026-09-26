package inventario.modelo;

/** Acciones que requieren autorización. Consultar productos y movimientos está permitido a todos. */
public enum Permiso {
    GESTIONAR_PRODUCTOS("registrar, editar o dar de baja productos"),
    REGISTRAR_COMPRAS("registrar compras"),
    REGISTRAR_VENTAS("registrar ventas"),
    AJUSTAR_STOCK("ajustar el stock"),
    GESTIONAR_PROVEEDORES("gestionar proveedores"),
    VER_REPORTES("ver reportes de ventas y ganancias"),
    GESTIONAR_USUARIOS("gestionar usuarios");

    private final String descripcion;

    Permiso(String descripcion) {
        this.descripcion = descripcion;
    }

    /** Texto para mensajes: "No tiene permiso para {descripcion}". */
    public String descripcion() {
        return descripcion;
    }
}
