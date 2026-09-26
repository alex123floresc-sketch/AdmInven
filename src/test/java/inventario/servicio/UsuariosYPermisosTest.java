package inventario.servicio;

import inventario.modelo.Movimiento;
import inventario.modelo.Rol;
import inventario.modelo.Usuario;
import inventario.persistencia.Transacciones;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsuariosYPermisosTest {

    private UsuarioRepositorioEnMemoria repo;
    private Sesion sesion;
    private UsuarioServicio usuarios;
    private MovimientoRepositorioEnMemoria movimientos;
    private InventarioServicio inventario;
    private ProveedorServicio proveedores;
    private ReporteServicio reportes;

    @BeforeEach
    void preparar() {
        repo = new UsuarioRepositorioEnMemoria();
        sesion = Sesion.nueva();
        usuarios = new UsuarioServicio(repo, sesion);
        ProductoRepositorioEnMemoria productos = new ProductoRepositorioEnMemoria();
        movimientos = new MovimientoRepositorioEnMemoria();
        ProveedorRepositorioEnMemoria repoProveedores = new ProveedorRepositorioEnMemoria();
        Clock reloj = Clock.systemDefaultZone();
        inventario = new InventarioServicio(productos, movimientos, Transacciones.NINGUNA,
                reloj, sesion);
        proveedores = new ProveedorServicio(repoProveedores, sesion);
        reportes = new ReporteServicio(productos, movimientos, repoProveedores, reloj, sesion);
    }

    private static char[] clave(String texto) {
        return texto.toCharArray();
    }

    /** Administrador "admin" y vendedor "luis"; deja la sesión cerrada. */
    private void crearAdminYVendedor() {
        usuarios.crearAdministradorInicial("admin", "Ana Torres", clave("admin123"));
        usuarios.crearUsuario("luis", "Luis Quispe", Rol.VENDEDOR, clave("vende2026"));
        usuarios.cerrarSesion();
    }

    // ---- Contraseñas ----

    @Test
    void hashIncluyeSalAleatoriaYSeVerifica() {
        String h1 = Contrasenas.hashear(clave("secreto123"));
        String h2 = Contrasenas.hashear(clave("secreto123"));

        assertNotEquals(h1, h2);
        assertTrue(h1.startsWith("pbkdf2-sha256$"));
        assertFalse(h1.contains("secreto123"));
        assertTrue(Contrasenas.verificar(clave("secreto123"), h1));
        assertFalse(Contrasenas.verificar(clave("secreto124"), h1));
        assertFalse(Contrasenas.verificar(clave("x"), "formato-invalido"));
    }

    @Test
    void reglasDeContrasena() {
        assertEquals("La contraseña debe tener al menos 8 caracteres.", Contrasenas.problema(clave("ab1")));
        assertEquals("La contraseña debe combinar letras y números.", Contrasenas.problema(clave("solamenteletras")));
        assertEquals(null, Contrasenas.problema(clave("letras123")));
    }

    @Test
    void laContrasenaRecibidaSeBorraDeLaMemoria() {
        crearAdminYVendedor();
        char[] contrasena = clave("admin123");

        usuarios.iniciarSesion("admin", contrasena);

        assertArrayEquals(new char[8], contrasena);
    }

    // ---- Sesión ----

    @Test
    void primeraEjecucionCreaElAdministradorYLoDejaConectado() {
        assertTrue(usuarios.requiereConfiguracionInicial());

        Usuario admin = usuarios.crearAdministradorInicial(" Admin ", "Ana Torres", clave("admin123"));

        assertEquals("admin", admin.nombreUsuario());
        assertEquals(Rol.ADMINISTRADOR, admin.rol());
        assertEquals("admin", sesion.nombreUsuario());
        assertFalse(usuarios.requiereConfiguracionInicial());
        assertThrows(InventarioException.class,
                () -> usuarios.crearAdministradorInicial("otro", "Otro", clave("otro1234")));
    }

    @Test
    void iniciarSesionConCredencialesCorrectasEIncorrectas() {
        crearAdminYVendedor();

        assertEquals(Rol.VENDEDOR, usuarios.iniciarSesion("LUIS", clave("vende2026")).rol());
        usuarios.cerrarSesion();

        InventarioException malaClave = assertThrows(InventarioException.class,
                () -> usuarios.iniciarSesion("luis", clave("equivocada1")));
        InventarioException inexistente = assertThrows(InventarioException.class,
                () -> usuarios.iniciarSesion("nadie", clave("vende2026")));
        assertEquals(malaClave.getMessage(), inexistente.getMessage());
        assertTrue(sesion.usuario().isEmpty());
    }

    @Test
    void usuarioDesactivadoNoPuedeEntrar() {
        crearAdminYVendedor();
        usuarios.iniciarSesion("admin", clave("admin123"));
        usuarios.desactivar("luis");
        usuarios.cerrarSesion();

        assertThrows(InventarioException.class, () -> usuarios.iniciarSesion("luis", clave("vende2026")));
    }

    @Test
    void cambiarMiContrasenaExigeLaActual() {
        crearAdminYVendedor();
        usuarios.iniciarSesion("luis", clave("vende2026"));

        assertThrows(InventarioException.class,
                () -> usuarios.cambiarMiContrasena(clave("mal12345"), clave("nueva2026")));
        usuarios.cambiarMiContrasena(clave("vende2026"), clave("nueva2026"));
        usuarios.cerrarSesion();

        assertEquals("luis", usuarios.iniciarSesion("luis", clave("nueva2026")).nombreUsuario());
    }

    // ---- Administración de usuarios ----

    @Test
    void soloElAdministradorGestionaUsuarios() {
        crearAdminYVendedor();
        usuarios.iniciarSesion("luis", clave("vende2026"));

        InventarioException e = assertThrows(InventarioException.class,
                () -> usuarios.crearUsuario("pepe", "Pepe", Rol.ADMINISTRADOR, clave("pepe1234")));
        assertEquals("Su rol (Vendedor) no permite gestionar usuarios.", e.getMessage());
        assertThrows(InventarioException.class, usuarios::listar);
    }

    @Test
    void nombreDeUsuarioRepetidoOInvalidoSeRechaza() {
        crearAdminYVendedor();
        usuarios.iniciarSesion("admin", clave("admin123"));

        assertThrows(InventarioException.class,
                () -> usuarios.crearUsuario("Luis", "Otro Luis", Rol.VENDEDOR, clave("otro1234")));
        assertThrows(InventarioException.class,
                () -> usuarios.crearUsuario("con espacio", "X", Rol.VENDEDOR, clave("otro1234")));
    }

    @Test
    void siempreQuedaUnAdministradorActivo() {
        crearAdminYVendedor();
        usuarios.iniciarSesion("admin", clave("admin123"));

        assertThrows(InventarioException.class, () -> usuarios.cambiarRol("admin", Rol.VENDEDOR));
        assertThrows(InventarioException.class, () -> usuarios.desactivar("admin"));

        usuarios.crearUsuario("jefa", "Jefa", Rol.ADMINISTRADOR, clave("jefa2026"));
        usuarios.cambiarRol("admin", Rol.VENDEDOR);
        assertEquals(Rol.VENDEDOR, repo.buscar("admin").orElseThrow().rol());
    }

    @Test
    void restablecerContrasenaPermiteEntrarConLaNueva() {
        crearAdminYVendedor();
        usuarios.iniciarSesion("admin", clave("admin123"));
        usuarios.restablecerContrasena("luis", clave("temporal1"));
        usuarios.cerrarSesion();

        assertEquals("luis", usuarios.iniciarSesion("luis", clave("temporal1")).nombreUsuario());
    }

    // ---- Permisos en las operaciones ----

    @Test
    void vendedorSoloPuedeRegistrarVentas() {
        crearAdminYVendedor();
        usuarios.iniciarSesion("admin", clave("admin123"));
        inventario.registrarProducto("A1", "Arroz", "", new BigDecimal("5"), new BigDecimal("4"), 10, 0);
        usuarios.cerrarSesion();
        usuarios.iniciarSesion("luis", clave("vende2026"));

        assertEquals(8, inventario.registrarSalida("A1", 2, "").getStock());
        assertThrows(InventarioException.class, () -> inventario.registrarEntrada("A1", 1, ""));
        assertThrows(InventarioException.class, () -> inventario.ajustarStock("A1", 0, ""));
        assertThrows(InventarioException.class,
                () -> inventario.registrarProducto("B2", "Sal", "", BigDecimal.ONE, 0, 0));
        assertThrows(InventarioException.class,
                () -> inventario.actualizarProducto("A1", "X", "", BigDecimal.ONE, 0));
        assertThrows(InventarioException.class, () -> inventario.darDeBajaProducto("A1"));
        assertThrows(InventarioException.class, () -> proveedores.registrar("Andina", "", "", ""));
        assertThrows(InventarioException.class, () -> reportes.ventas(LocalDate.now(), LocalDate.now()));
        // Consultar sí puede.
        assertEquals(1, inventario.listarProductos().size());
    }

    @Test
    void sinSesionNoSePuedeModificarNada() {
        InventarioException e = assertThrows(InventarioException.class,
                () -> inventario.registrarProducto("A1", "Arroz", "", BigDecimal.ONE, 0, 0));
        assertEquals("Debe iniciar sesión para registrar, editar o dar de baja productos.", e.getMessage());
    }

    @Test
    void cadaMovimientoGuardaQuienLoRegistro() {
        crearAdminYVendedor();
        usuarios.iniciarSesion("admin", clave("admin123"));
        inventario.registrarProducto("A1", "Arroz", "", new BigDecimal("5"), 10, 0);
        usuarios.cerrarSesion();
        usuarios.iniciarSesion("luis", clave("vende2026"));
        inventario.registrarSalida("A1", 1, "");

        assertEquals(List.of("admin", "luis"),
                movimientos.listar().stream().map(Movimiento::usuario).toList());
    }
}
