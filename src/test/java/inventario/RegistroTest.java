package inventario;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistroTest {

    @TempDir
    Path carpeta;

    @AfterEach
    void cerrar() {
        Registro.cerrar();
    }

    @Test
    void escribeAvisosYErroresConSuTrazaEnLaCarpetaDeDatos() throws IOException {
        Path datos = carpeta.resolve("datos con espacios");
        Registro.configurar(datos, false);
        Logger registro = Logger.getLogger("inventario.prueba.Clase");
        Level anterior = Logger.getLogger("inventario").getLevel();
        Logger.getLogger("inventario").setLevel(Level.INFO);
        try {
            registro.warning("Intento de acceso fallido con el usuario \"ñandú\"");
            registro.log(Level.SEVERE, "Error al guardar", new IllegalStateException("disco lleno"));
        } finally {
            Logger.getLogger("inventario").setLevel(anterior);
        }
        Registro.cerrar();

        String texto = Files.readString(datos.resolve("registro0.log"), StandardCharsets.UTF_8);
        assertTrue(texto.contains("AVISO [prueba.Clase] Intento de acceso fallido con el usuario \"ñandú\""), texto);
        assertTrue(texto.contains("ERROR [prueba.Clase] Error al guardar"), texto);
        assertTrue(texto.contains("java.lang.IllegalStateException: disco lleno"), texto);
    }

    @Test
    void noBorraLoEscritoEnEjecucionesAnteriores() throws IOException {
        Logger registro = Logger.getLogger("inventario.prueba.Clase");
        Registro.configurar(carpeta, false);
        registro.severe("primera ejecución");
        Registro.cerrar();
        Registro.configurar(carpeta, false);
        registro.severe("segunda ejecución");
        Registro.cerrar();

        String texto = Files.readString(carpeta.resolve("registro0.log"), StandardCharsets.UTF_8);
        assertTrue(texto.contains("primera ejecución") && texto.contains("segunda ejecución"), texto);
    }
}
