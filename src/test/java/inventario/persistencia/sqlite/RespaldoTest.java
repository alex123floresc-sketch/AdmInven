package inventario.persistencia.sqlite;

import inventario.modelo.Permiso;
import inventario.modelo.Rol;
import inventario.servicio.InventarioException;
import inventario.servicio.InventarioServicio;
import inventario.servicio.RespaldoServicio;
import inventario.servicio.Sesion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Copias de seguridad de la base SQLite mientras está abierta. */
class RespaldoTest {

    @TempDir
    Path carpeta;

    private BaseDeDatos bd;
    private InventarioServicio inventario;
    private RespaldoServicio respaldos;

    @BeforeEach
    void abrir() {
        bd = BaseDeDatos.abrir(carpeta.resolve("inventario.db"));
        inventario = new InventarioServicio(new SqliteProductoRepositorio(bd), new SqliteMovimientoRepositorio(bd), bd);
        respaldos = new RespaldoServicio(bd, Clock.systemDefaultZone(), Sesion.sinRestricciones());
        inventario.registrarProducto("A1", "Arroz", "Abarrotes", new BigDecimal("4.50"), 20, 5);
        inventario.registrarSalida("A1", 3, "Venta");
    }

    @AfterEach
    void cerrar() {
        bd.close();
    }

    @Test
    void laCopiaTieneTodosLosDatosYElMismoEsquema() {
        Path copia = carpeta.resolve("respaldos/copia.db");
        respaldos.crearCopia(copia);

        try (BaseDeDatos restaurada = BaseDeDatos.abrir(copia)) {
            InventarioServicio desdeCopia = new InventarioServicio(new SqliteProductoRepositorio(restaurada),
                    new SqliteMovimientoRepositorio(restaurada), restaurada);
            assertEquals(17, desdeCopia.obtener("A1").getStock());
            assertEquals(2, desdeCopia.historial("A1").size());
            assertEquals(bd.versionEsquema(), restaurada.versionEsquema());
        }
        // La base original sigue funcionando después de copiarla.
        inventario.registrarSalida("A1", 1, "Venta");
        assertEquals(16, inventario.obtener("A1").getStock());
    }

    @Test
    void unaCopiaNuevaReemplazaLaAnteriorSinDejarTemporales() {
        Path copia = carpeta.resolve("copia.db");
        respaldos.crearCopia(copia);
        inventario.registrarProducto("B2", "Azúcar", "Abarrotes", new BigDecimal("3.80"), 10, 2);
        respaldos.crearCopia(copia);

        try (BaseDeDatos restaurada = BaseDeDatos.abrir(copia)) {
            assertTrue(new SqliteProductoRepositorio(restaurada).buscarPorCodigo("B2").isPresent());
        }
        assertFalse(Files.exists(carpeta.resolve("copia.db.tmp")));
    }

    @Test
    void noPermiteReemplazarLaBaseEnUso() {
        InventarioException e = assertThrows(InventarioException.class,
                () -> respaldos.crearCopia(carpeta.resolve("./inventario.db")));
        assertTrue(e.getMessage().contains("en uso"));
        assertEquals(20 - 3, inventario.obtener("A1").getStock());
    }

    @Test
    void soloElAdministradorPuedeCrearCopias() {
        assertTrue(Rol.ADMINISTRADOR.permite(Permiso.RESPALDAR_DATOS));
        assertFalse(Rol.VENDEDOR.permite(Permiso.RESPALDAR_DATOS));

        RespaldoServicio sinSesion = new RespaldoServicio(bd, Clock.systemDefaultZone(), Sesion.nueva());
        Path copia = carpeta.resolve("copia.db");
        assertThrows(InventarioException.class, () -> sinSesion.crearCopia(copia));
        assertFalse(Files.exists(copia));
    }

    @Test
    void elNombreSugeridoLlevaFechaYHora() {
        Clock reloj = Clock.fixed(Instant.parse("2026-09-29T15:30:00Z"), ZoneId.of("UTC"));
        assertEquals("inventario-respaldo-2026-09-29-1530.db",
                new RespaldoServicio(bd, reloj, Sesion.sinRestricciones()).nombreSugerido());
    }
}
