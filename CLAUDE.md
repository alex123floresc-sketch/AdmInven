# Administrador de inventario

Proyecto académico: aplicación Java 26 + JavaFX + SQLite (Maven) para gestionar inventario, con versión web
(Javalin, `--web`) y empaquetado como programa de Windows (jpackage, `empaquetado/`).
Se desarrolló por fases siguiendo la guía del proyecto: https://claude.ai/code/artifact/1b885b59-d2be-4f48-9343-422034d6807c
(las 6 fases están hechas; ahora toca pulir).

## Convenciones
- Todo en español: identificadores, mensajes al usuario, comentarios y commits.
- Proyecto Maven: código en `src/main/java/inventario/`, pruebas JUnit 5 en `src/test/java/inventario/`.
- Capas: `modelo` → `persistencia` → `servicio` → `ui` (`ui/fx` = JavaFX, `ui/web` = servidor Javalin,
  `ui/MenuConsola` = consola).
  - La UI solo habla con los servicios; los servicios solo con las interfaces de repositorio.
  - Reglas de negocio en los servicios; lanzan `InventarioException` con mensajes aptos para el usuario.
  - Permisos: cada operación protegida llama a `sesion.requerir(Permiso.X)` en el servicio (no solo en la UI).
  - Almacenamiento: SQLite (`persistencia/sqlite`). Cambios de esquema = nuevo script
    `src/main/resources/db/V<n>.sql`, nunca editar uno existente.
  - Operaciones que tocan varias tablas van dentro de `Transacciones.ejecutar`.
  - `Aplicacion` es el único lugar que crea repositorios y servicios.
- Dinero con `BigDecimal` (2 decimales), nunca `double`.
- Códigos de producto normalizados con `Producto.normalizarCodigo`; usuarios con `Usuario.normalizarNombreUsuario`.
- Web: `ui/web/Rutas` solo traduce HTTP ⇄ servicios y responde con los registros de `ui/web/Json` (nunca enviar
  costos a quien no tiene `VER_REPORTES`). Toda ruta nueva va envuelta en `publica(...)`/`privada(...)`: atienden
  de a una petición porque SQLite usa una sola conexión. Página en `resources/inventario/ui/web/publico`, sin
  librerías externas; todo texto dinámico pasa por `esc()` y no se usan estilos en línea (CSP `default-src 'self'`).
- Estilos de la ventana en `src/main/resources/inventario/ui/fx/estilos.css` (colores como variables en `.root`).

## Compilar y probar
Maven no está instalado globalmente: usar siempre el wrapper (`./mvnw` en Bash, `.\mvnw.cmd` en PowerShell).
```sh
./mvnw -B test                     # compilar y ejecutar pruebas
./mvnw -B -q package               # genera target/administrador-de-inventario-1.0-SNAPSHOT.jar (con dependencias)
java -Dstdout.encoding=UTF-8 -Dstdin.encoding=UTF-8 -jar target/administrador-de-inventario-1.0-SNAPSHOT.jar --consola --demo <carpeta>
java -jar target/administrador-de-inventario-1.0-SNAPSHOT.jar --web --demo --puerto=7071 <carpeta>   # probar la web
powershell -ExecutionPolicy Bypass -File empaquetado\crear-app-escritorio.ps1 [-SinInstalar]         # app de Windows
```
- Si se toca la página web, revisarla en el navegador (también a 390 px de ancho) y la consola sin errores.
- Probar con una carpeta de datos temporal, nunca con `data/` o `demo/` del usuario.
- Tras cada cambio: compilar sin advertencias (`-Xlint:all` está activo) y pasar las pruebas; si toca la UI,
  revisar capturas de pantalla o ejecutar un escenario por stdin en modo consola.
- Modo `--demo`: usuarios `admin`/`admin123` y `vendedor`/`vendedor123`.

## Flujo de trabajo
- Cada cambio pedido: commit (mensaje en español, imperativo) y push a origin/main (GitHub AdmInven).
- En PowerShell 5.1 los mensajes de commit con comillas se rompen: usar Bash con `git commit -F - <<'EOF'`.
