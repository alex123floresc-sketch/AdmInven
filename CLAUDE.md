# Administrador de inventario

Proyecto académico: aplicación de escritorio Java 26 + JavaFX + SQLite (Maven) para gestionar inventario.
Se desarrolló por fases siguiendo la guía del proyecto: https://claude.ai/code/artifact/1b885b59-d2be-4f48-9343-422034d6807c
(las 6 fases están hechas; ahora toca pulir).

## Convenciones
- Todo en español: identificadores, mensajes al usuario, comentarios y commits.
- Proyecto Maven: código en `src/main/java/inventario/`, pruebas JUnit 5 en `src/test/java/inventario/`.
- Capas: `modelo` → `persistencia` → `servicio` → `ui` (`ui/fx` = JavaFX, `ui/MenuConsola` = consola).
  - La UI solo habla con los servicios; los servicios solo con las interfaces de repositorio.
  - Reglas de negocio en los servicios; lanzan `InventarioException` con mensajes aptos para el usuario.
  - Permisos: cada operación protegida llama a `sesion.requerir(Permiso.X)` en el servicio (no solo en la UI).
  - Almacenamiento: SQLite (`persistencia/sqlite`). Cambios de esquema = nuevo script
    `src/main/resources/db/V<n>.sql`, nunca editar uno existente.
  - Operaciones que tocan varias tablas van dentro de `Transacciones.ejecutar`.
  - `Aplicacion` es el único lugar que crea repositorios y servicios.
- Dinero con `BigDecimal` (2 decimales), nunca `double`.
- Códigos de producto normalizados con `Producto.normalizarCodigo`; usuarios con `Usuario.normalizarNombreUsuario`.
- Estilos de la ventana en `src/main/resources/inventario/ui/fx/estilos.css` (colores como variables en `.root`).

## Compilar y probar
Maven no está instalado globalmente: usar siempre el wrapper (`./mvnw` en Bash, `.\mvnw.cmd` en PowerShell).
```sh
./mvnw -B test                     # compilar y ejecutar pruebas
./mvnw -B -q package               # genera target/administrador-de-inventario-1.0-SNAPSHOT.jar (con dependencias)
java -Dstdout.encoding=UTF-8 -Dstdin.encoding=UTF-8 -jar target/administrador-de-inventario-1.0-SNAPSHOT.jar --consola --demo <carpeta>
```
- Probar con una carpeta de datos temporal, nunca con `data/` o `demo/` del usuario.
- Tras cada cambio: compilar sin advertencias (`-Xlint:all` está activo) y pasar las pruebas; si toca la UI,
  revisar capturas de pantalla o ejecutar un escenario por stdin en modo consola.
- Modo `--demo`: usuarios `admin`/`admin123` y `vendedor`/`vendedor123`.

## Flujo de trabajo
- Cada cambio pedido: commit (mensaje en español, imperativo) y push a origin/main (GitHub AdmInven).
- En PowerShell 5.1 los mensajes de commit con comillas se rompen: usar Bash con `git commit -F - <<'EOF'`.
