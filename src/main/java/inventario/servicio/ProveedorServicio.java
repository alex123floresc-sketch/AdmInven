package inventario.servicio;

import inventario.modelo.Permiso;
import inventario.modelo.Proveedor;
import inventario.persistencia.ProveedorRepositorio;

import java.util.List;
import java.util.Locale;

/** Registro de proveedores: alta, edición y baja lógica. */
public class ProveedorServicio {

    private final ProveedorRepositorio proveedores;
    private final Sesion sesion;

    public ProveedorServicio(ProveedorRepositorio proveedores) {
        this(proveedores, Sesion.sinRestricciones());
    }

    public ProveedorServicio(ProveedorRepositorio proveedores, Sesion sesion) {
        this.proveedores = proveedores;
        this.sesion = sesion;
    }

    public Proveedor registrar(String nombre, String documento, String telefono, String email) {
        sesion.requerir(Permiso.GESTIONAR_PROVEEDORES);
        Proveedor nuevo = crearValidado(null, nombre, documento, telefono, email, true);
        validarNombreDisponible(nuevo.nombre(), null);
        return guardar(nuevo);
    }

    public Proveedor actualizar(long id, String nombre, String documento, String telefono, String email) {
        sesion.requerir(Permiso.GESTIONAR_PROVEEDORES);
        Proveedor actual = obtener(id);
        Proveedor actualizado = crearValidado(id, nombre, documento, telefono, email, actual.activo());
        validarNombreDisponible(actualizado.nombre(), id);
        return guardar(actualizado);
    }

    public void darDeBaja(long id) {
        sesion.requerir(Permiso.GESTIONAR_PROVEEDORES);
        guardar(obtener(id).conActivo(false));
    }

    public void reactivar(long id) {
        sesion.requerir(Permiso.GESTIONAR_PROVEEDORES);
        guardar(obtener(id).conActivo(true));
    }

    public Proveedor obtener(long id) {
        return proveedores.buscarPorId(id)
                .orElseThrow(() -> new InventarioException("No existe el proveedor con id " + id + "."));
    }

    /** Proveedores activos, para elegir en una compra. */
    public List<Proveedor> listarActivos() {
        return proveedores.listar().stream().filter(Proveedor::activo).toList();
    }

    /** Incluye los dados de baja y sus datos de contacto: solo para quien gestiona proveedores. */
    public List<Proveedor> listarTodos() {
        sesion.requerir(Permiso.GESTIONAR_PROVEEDORES);
        return proveedores.listar();
    }

    private void validarNombreDisponible(String nombre, Long idPropio) {
        String buscado = nombre.toLowerCase(Locale.ROOT);
        boolean repetido = proveedores.listar().stream()
                .anyMatch(p -> p.nombre().toLowerCase(Locale.ROOT).equals(buscado) && !p.id().equals(idPropio));
        if (repetido) {
            throw new InventarioException("Ya existe un proveedor llamado \"" + nombre + "\".");
        }
    }

    private static Proveedor crearValidado(Long id, String nombre, String documento, String telefono, String email,
                                           boolean activo) {
        try {
            return new Proveedor(id, nombre, documento, telefono, email, activo);
        } catch (IllegalArgumentException e) {
            throw new InventarioException(e.getMessage());
        }
    }

    private Proveedor guardar(Proveedor proveedor) {
        try {
            return proveedores.guardar(proveedor);
        } catch (RuntimeException e) {
            throw new InventarioException("No se pudo guardar el proveedor: " + e.getMessage(), e);
        }
    }
}
