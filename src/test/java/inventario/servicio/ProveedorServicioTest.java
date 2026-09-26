package inventario.servicio;

import inventario.modelo.Proveedor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProveedorServicioTest {

    private ProveedorServicio servicio;

    @BeforeEach
    void preparar() {
        servicio = new ProveedorServicio(new ProveedorRepositorioEnMemoria());
    }

    @Test
    void registrarAsignaIdYLimpiaLosDatos() {
        Proveedor p = servicio.registrar("  Distribuidora Andina  ", " 20512345678 ", "", "ventas@andina.pe");

        assertNotNull(p.id());
        assertEquals("Distribuidora Andina", p.nombre());
        assertEquals("20512345678", p.documento());
    }

    @Test
    void nombreRepetidoSeRechazaSinDistinguirMayusculas() {
        servicio.registrar("Andina", "", "", "");

        InventarioException e = assertThrows(InventarioException.class,
                () -> servicio.registrar("ANDINA", "", "", ""));
        assertEquals("Ya existe un proveedor llamado \"ANDINA\".", e.getMessage());
    }

    @Test
    void documentoYCorreoSeValidan() {
        assertThrows(InventarioException.class, () -> servicio.registrar("X", "123", "", ""));
        assertThrows(InventarioException.class, () -> servicio.registrar("X", "2051234567A", "", ""));
        assertThrows(InventarioException.class, () -> servicio.registrar("X", "", "", "sin-arroba"));
        assertThrows(InventarioException.class, () -> servicio.registrar(" ", "", "", ""));
        assertNotNull(servicio.registrar("Con DNI", "45678912", "", "").id());
    }

    @Test
    void actualizarPuedeConservarSuPropioNombre() {
        Proveedor p = servicio.registrar("Andina", "", "", "");

        Proveedor actualizado = servicio.actualizar(p.id(), "Andina", "20512345678", "999", "");

        assertEquals("999", actualizado.telefono());
        assertEquals(1, servicio.listarTodos().size());
    }

    @Test
    void bajaLoQuitaDeLosActivosYReactivarLoDevuelve() {
        Proveedor a = servicio.registrar("Andina", "", "", "");
        servicio.registrar("Bebidas Norte", "", "", "");

        servicio.darDeBaja(a.id());

        assertEquals(List.of("Bebidas Norte"), servicio.listarActivos().stream().map(Proveedor::nombre).toList());
        assertFalse(servicio.obtener(a.id()).activo());
        servicio.reactivar(a.id());
        assertEquals(2, servicio.listarActivos().size());
    }

    @Test
    void obtenerInexistenteFalla() {
        assertThrows(InventarioException.class, () -> servicio.obtener(99));
    }
}
