package inventario.modelo;

import java.util.EnumSet;
import java.util.Set;

public enum Rol {
    /** Acceso completo, incluida la gestión de usuarios. */
    ADMINISTRADOR("Administrador", EnumSet.allOf(Permiso.class)),
    /** Atiende el mostrador: consulta productos y registra ventas. */
    VENDEDOR("Vendedor", EnumSet.of(Permiso.REGISTRAR_VENTAS));

    private final String nombre;
    private final Set<Permiso> permisos;

    Rol(String nombre, Set<Permiso> permisos) {
        this.nombre = nombre;
        this.permisos = Set.copyOf(permisos);
    }

    public boolean permite(Permiso permiso) {
        return permisos.contains(permiso);
    }

    @Override
    public String toString() {
        return nombre;
    }
}
