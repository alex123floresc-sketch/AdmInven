# Administrador de inventario

Proyecto académico: aplicación Java (JDK 26) para gestionar productos y stock. Se desarrolla por fases
siguiendo la guía del proyecto: https://claude.ai/code/artifact/1b885b59-d2be-4f48-9343-422034d6807c

## Convenciones
- Todo en español: identificadores, mensajes al usuario, comentarios y commits.
- Proyecto Maven: código en `src/main/java/inventario/`, pruebas JUnit 5 en `src/test/java/inventario/`.
- Arquitectura por capas en `src/main/java/inventario/`: `modelo` → `persistencia` → `servicio` → `ui`.
  - La UI solo habla con `InventarioServicio`; el servicio solo con las interfaces de repositorio.
  - Las reglas de negocio van en el servicio y lanzan `InventarioException` con mensajes aptos para el usuario.
  - Un nuevo almacenamiento = nueva implementación de `ProductoRepositorio` / `MovimientoRepositorio`.
- Dinero con `BigDecimal` (2 decimales), nunca `double`.
- Los códigos de producto se normalizan con `Producto.normalizarCodigo` (mayúsculas, sin espacios laterales).

## Compilar y probar
Maven no está instalado globalmente: usar siempre el wrapper (`./mvnw` en Bash, `.\mvnw.cmd` en PowerShell).
```sh
./mvnw -B test                     # compilar y ejecutar pruebas
./mvnw -B -q package               # genera target/administrador-de-inventario-1.0-SNAPSHOT.jar
java -Dstdout.encoding=UTF-8 -Dstdin.encoding=UTF-8 -jar target/administrador-de-inventario-1.0-SNAPSHOT.jar <carpeta-datos>
```
- Probar con una carpeta de datos temporal, nunca con `data/` del usuario.
- Tras cada cambio: compilar sin advertencias (`-Xlint:all` está activo), pasar las pruebas y, si toca la UI,
  ejecutar un escenario por stdin.

## Flujo de trabajo
- Trabajar una fase o tarea a la vez; commit al terminar cada tarea (mensajes en español, en imperativo).
- Al completar una tarea, marcarla en la guía del proyecto.
