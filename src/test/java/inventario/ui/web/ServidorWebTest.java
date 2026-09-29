package inventario.ui.web;

import inventario.Aplicacion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prueba la API web de punta a punta con los datos de demostración. */
class ServidorWebTest {

    @TempDir
    Path carpeta;

    private Aplicacion app;
    private ServidorWeb servidor;

    @BeforeEach
    void iniciar() {
        app = Aplicacion.iniciarDemo(carpeta);
        servidor = new ServidorWeb(app).iniciar(0);
    }

    @AfterEach
    void detener() {
        servidor.close();
        app.close();
    }

    private record Respuesta(int estado, String cuerpo) {
    }

    /** Cliente con sus propias cookies, como un navegador distinto. */
    private final class Navegador {
        private final HttpClient cliente = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

        Respuesta pedir(String metodo, String ruta, String json, boolean conCabecera) {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + servidor.puerto() + ruta))
                    .method(metodo, json == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(json))
                    .header("Content-Type", "application/json");
            if (conCabecera) {
                b.header(ServidorWeb.CABECERA_PROPIA, "1");
            }
            try {
                HttpResponse<String> r = cliente.send(b.build(), HttpResponse.BodyHandlers.ofString());
                return new Respuesta(r.statusCode(), r.body());
            } catch (IOException | InterruptedException e) {
                throw new AssertionError(e);
            }
        }

        Respuesta pedir(String metodo, String ruta, String json) {
            return pedir(metodo, ruta, json, true);
        }

        Respuesta entrar(String usuario, String clave) {
            return pedir("POST", "/api/sesion", "{\"usuario\":\"" + usuario + "\",\"contrasena\":\"" + clave + "\"}");
        }
    }

    @Test
    void sirveLaPaginaYExigeSesionParaLosDatos() {
        Navegador n = new Navegador();
        Respuesta pagina = n.pedir("GET", "/", null);
        assertEquals(200, pagina.estado());
        assertTrue(pagina.cuerpo().contains("Administrador de Inventario"));
        assertEquals(401, n.pedir("GET", "/api/productos", null).estado());
    }

    @Test
    void credencialesIncorrectasSeRechazan() {
        Respuesta r = new Navegador().entrar("admin", "equivocada1");
        assertEquals(400, r.estado());
        assertTrue(r.cuerpo().contains("Usuario o contraseña incorrectos."));
    }

    @Test
    void losCambiosExigenLaCabeceraPropia() {
        Navegador n = new Navegador();
        n.entrar("admin", "admin123");
        Respuesta sinCabecera = n.pedir("POST", "/api/productos/ARR-5K/movimientos",
                "{\"tipo\":\"SALIDA\",\"cantidad\":\"1\"}", false);
        assertEquals(400, sinCabecera.estado());
    }

    @Test
    void elVendedorVendePeroNoVeCostosNiReportes() {
        Navegador vendedor = new Navegador();
        assertEquals(200, vendedor.entrar("vendedor", "vendedor123").estado());

        String productos = vendedor.pedir("GET", "/api/productos", null).cuerpo();
        assertTrue(productos.contains("\"costo\":null"));
        assertFalse(productos.contains("\"costo\":1"));
        assertEquals(400, vendedor.pedir("GET", "/api/reportes", null).estado());
        assertEquals(400, vendedor.pedir("POST", "/api/productos/ARR-5K/movimientos",
                "{\"tipo\":\"ENTRADA\",\"cantidad\":\"5\"}").estado());

        Respuesta venta = vendedor.pedir("POST", "/api/productos/ARR-5K/movimientos",
                "{\"tipo\":\"SALIDA\",\"cantidad\":\"2\",\"nota\":\"web\"}");
        assertEquals(200, venta.estado());
        assertTrue(venta.cuerpo().contains("\"stock\":18"));
    }

    @Test
    void cadaNavegadorTieneSuPropiaSesion() {
        Navegador admin = new Navegador();
        Navegador vendedor = new Navegador();
        admin.entrar("admin", "admin123");
        vendedor.entrar("vendedor", "vendedor123");

        assertEquals(200, admin.pedir("GET", "/api/usuarios", null).estado());
        assertEquals(400, vendedor.pedir("GET", "/api/usuarios", null).estado());

        // Al desactivar al vendedor, su sesión abierta deja de funcionar.
        assertEquals(204, admin.pedir("POST", "/api/usuarios/vendedor/desactivar", null).estado());
        assertEquals(401, vendedor.pedir("GET", "/api/productos", null).estado());
    }

    @Test
    void cerrarSesionInvalidaLaCookie() {
        Navegador n = new Navegador();
        n.entrar("admin", "admin123");
        assertEquals(204, n.pedir("DELETE", "/api/sesion", null).estado());
        assertEquals(401, n.pedir("GET", "/api/resumen", null).estado());
    }
}
