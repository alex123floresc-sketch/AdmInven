package inventario.ui.web;

import inventario.Aplicacion;
import inventario.servicio.InventarioException;
import inventario.servicio.Sesion;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;
import io.javalin.http.SameSite;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JavalinJackson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Versión web: sirve la página ({@code resources/inventario/ui/web/publico}) y la API JSON en {@code /api}.
 * <p>
 * Cada persona que inicia sesión recibe sus propios servicios ({@link Aplicacion#serviciosPara(Sesion)}), así
 * los permisos se cumplen igual que en la ventana. La base SQLite usa una sola conexión, por eso las peticiones
 * a la API se atienden de una en una: para un negocio pequeño es más que suficiente y evita errores de datos.
 */
public final class ServidorWeb implements AutoCloseable {

    static final String COOKIE_SESION = "inventario_sesion";
    /** Cabecera que la página envía en cada cambio; un formulario de otro sitio no puede enviarla (CSRF). */
    static final String CABECERA_PROPIA = "X-Inventario";
    private static final Duration INACTIVIDAD_MAXIMA = Duration.ofHours(8);
    private static final int INTENTOS_FALLIDOS_MAXIMOS = 5;
    private static final Duration BLOQUEO_POR_INTENTOS = Duration.ofMinutes(2);
    private static final Set<String> METODOS_QUE_CAMBIAN = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Logger REGISTRO = LoggerFactory.getLogger(ServidorWeb.class);
    private static final SecureRandom AZAR = new SecureRandom();

    /** Sesión de una persona conectada a la página. */
    static final class SesionWeb {
        final Aplicacion.Servicios servicios;
        volatile Instant ultimoUso = Instant.now();

        SesionWeb(Aplicacion.Servicios servicios) {
            this.servicios = servicios;
        }
    }

    private record Intentos(int fallidos, Instant bloqueadoHasta) {
    }

    private final Aplicacion app;
    private final Map<String, SesionWeb> sesiones = new ConcurrentHashMap<>();
    private final Map<String, Intentos> intentos = new ConcurrentHashMap<>();
    private final ReentrantLock candado = new ReentrantLock(true);
    private final Javalin javalin;

    public ServidorWeb(Aplicacion app) {
        this.app = app;
        Rutas rutas = new Rutas(this);
        javalin = Javalin.create(config -> {
            config.startup.showJavalinBanner = false;
            config.startup.showOldJavalinVersionWarning = false;
            config.jsonMapper(new JavalinJackson(Json.mapeador(), false));
            config.staticFiles.add(archivos -> {
                archivos.hostedPath = "/";
                archivos.directory = "/inventario/ui/web/publico";
                archivos.location = Location.CLASSPATH;
                archivos.headers = Map.of("Cache-Control", "no-cache");
            });
            config.routes.before(ServidorWeb::cabecerasDeSeguridad);
            config.routes.before("/api/*", this::verificarOrigen);
            rutas.registrar(config.routes);
            config.routes.exception(InventarioException.class, (e, ctx) ->
                    ctx.status(HttpStatus.BAD_REQUEST).json(new Json.MensajeError(e.getMessage())));
            config.routes.exception(NoAutenticado.class, (e, ctx) ->
                    ctx.status(HttpStatus.UNAUTHORIZED).json(new Json.MensajeError(e.getMessage())));
            config.routes.exception(Exception.class, (e, ctx) -> {
                REGISTRO.error("Error al atender {} {}", ctx.method(), ctx.path(), e);
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .json(new Json.MensajeError("Ocurrió un error inesperado en el servidor."));
            });
        });
    }

    public ServidorWeb iniciar(int puerto) {
        javalin.start(puerto);
        return this;
    }

    public int puerto() {
        return javalin.port();
    }

    @Override
    public void close() {
        javalin.stop();
    }

    Aplicacion aplicacion() {
        return app;
    }

    // ---- Peticiones de a una ----

    /** Envuelve un manejador de la API para que use la base de datos sin interferir con otras peticiones. */
    Handler exclusivo(Handler manejador) {
        return ctx -> {
            candado.lock();
            try {
                manejador.handle(ctx);
            } finally {
                candado.unlock();
            }
        };
    }

    // ---- Sesiones ----

    /** Servicios de quien hace la petición; si no inició sesión (o fue desactivado) responde 401. */
    Aplicacion.Servicios servicios(Context ctx) {
        SesionWeb sesion = sesionActual(ctx).orElseThrow(NoAutenticado::new);
        // Si un administrador lo desactivó o le cambió el rol, se aplica desde ya.
        if (!sesion.servicios.usuarios().actualizarSesion()) {
            cerrarSesion(ctx);
            throw new NoAutenticado();
        }
        sesion.ultimoUso = Instant.now();
        return sesion.servicios;
    }

    Optional<SesionWeb> sesionActual(Context ctx) {
        purgarSesionesVencidas();
        return Optional.ofNullable(ctx.cookie(COOKIE_SESION)).map(sesiones::get);
    }

    /** Servicios nuevos para alguien que aún no inició sesión (acceso o configuración inicial). */
    Aplicacion.Servicios serviciosSinSesion() {
        return app.serviciosPara(Sesion.nueva());
    }

    void abrirSesion(Context ctx, Aplicacion.Servicios servicios) {
        cerrarSesion(ctx);
        byte[] bytes = new byte[32];
        AZAR.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sesiones.put(token, new SesionWeb(servicios));
        Cookie cookie = new Cookie(COOKIE_SESION, token);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSameSite(SameSite.STRICT);
        cookie.setSecure(esHttps(ctx));
        ctx.cookie(cookie);
    }

    void cerrarSesion(Context ctx) {
        Optional.ofNullable(ctx.cookie(COOKIE_SESION)).map(sesiones::remove)
                .ifPresent(s -> s.servicios.usuarios().cerrarSesion());
        ctx.removeCookie(COOKIE_SESION, "/");
    }

    private void purgarSesionesVencidas() {
        Instant limite = Instant.now().minus(INACTIVIDAD_MAXIMA);
        sesiones.values().removeIf(s -> s.ultimoUso.isBefore(limite));
    }

    // ---- Intentos de acceso ----

    /** Tras varios intentos fallidos con un usuario desde la misma dirección, se pide esperar. */
    void verificarIntentos(Context ctx, String usuario) {
        Intentos i = intentos.get(claveIntentos(ctx, usuario));
        if (i != null && i.bloqueadoHasta() != null && Instant.now().isBefore(i.bloqueadoHasta())) {
            throw new InventarioException("Demasiados intentos fallidos. Espere unos minutos y vuelva a intentarlo.");
        }
    }

    void registrarIntento(Context ctx, String usuario, boolean exitoso) {
        String clave = claveIntentos(ctx, usuario);
        if (exitoso) {
            intentos.remove(clave);
            return;
        }
        intentos.compute(clave, (k, previo) -> {
            int fallidos = (previo == null || previo.bloqueadoHasta() != null ? 0 : previo.fallidos()) + 1;
            return fallidos >= INTENTOS_FALLIDOS_MAXIMOS
                    ? new Intentos(0, Instant.now().plus(BLOQUEO_POR_INTENTOS))
                    : new Intentos(fallidos, null);
        });
    }

    private static String claveIntentos(Context ctx, String usuario) {
        return ctx.ip() + "|" + (usuario == null ? "" : usuario.strip().toLowerCase(Locale.ROOT));
    }

    // ---- Seguridad ----

    private static void cabecerasDeSeguridad(Context ctx) {
        ctx.header("X-Content-Type-Options", "nosniff");
        ctx.header("X-Frame-Options", "DENY");
        ctx.header("Referrer-Policy", "same-origin");
        ctx.header("Content-Security-Policy",
                "default-src 'self'; img-src 'self' data:; object-src 'none'; frame-ancestors 'none'");
    }

    private void verificarOrigen(Context ctx) {
        ctx.header("Cache-Control", "no-store");
        if (METODOS_QUE_CAMBIAN.contains(ctx.method().name()) && ctx.header(CABECERA_PROPIA) == null) {
            throw new InventarioException("Petición rechazada: falta la cabecera " + CABECERA_PROPIA + ".");
        }
    }

    private static boolean esHttps(Context ctx) {
        // Detrás de un proxy (Render, Railway, nginx...) el protocolo original llega en esta cabecera.
        return "https".equalsIgnoreCase(ctx.header("X-Forwarded-Proto")) || "https".equals(ctx.scheme());
    }

    /** Petición sin sesión válida. */
    static final class NoAutenticado extends RuntimeException {
        private static final long serialVersionUID = 1L;

        NoAutenticado() {
            super("Su sesión terminó. Vuelva a iniciar sesión.");
        }
    }
}
