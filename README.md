# Administrador de inventario

Aplicación de consola en Java (JDK 26, proyecto Maven) para gestionar productos y su stock.

## Funcionalidades

- Alta, edición, baja y búsqueda de productos (código, nombre, categoría, precio, stock mínimo).
- Entradas, salidas y ajustes de stock por conteo físico, con validación de stock suficiente.
- Historial de movimientos por producto.
- Alertas y listado de productos en o por debajo del stock mínimo.
- Resumen: unidades totales, valor del inventario y últimos movimientos.
- Persistencia automática en `data/productos.csv` y `data/movimientos.csv` (UTF-8, separador `;`).

## Estructura

```
src/main/java/inventario/
├── Main.java                 Punto de entrada y armado de dependencias
├── modelo/                   Producto, Movimiento, TipoMovimiento
├── persistencia/             Repositorios (interfaces + implementación en CSV)
├── servicio/                 Reglas de negocio (InventarioServicio)
└── ui/                       Menú de consola
```

La interfaz solo habla con `InventarioServicio`, y este solo con las interfaces de repositorio,
así que se puede cambiar la UI (p. ej. Swing/JavaFX) o el almacenamiento (p. ej. SQLite) sin tocar el resto.

## Ejecutar

Solo se necesita el JDK 26: el Maven Wrapper (`mvnw`) descarga Maven automáticamente la primera vez.

Desde IntelliJ: abrir la carpeta (se importa como proyecto Maven) y ejecutar `inventario.Main`.

Desde la terminal (en PowerShell usar `.\mvnw.cmd` en lugar de `./mvnw`):

```sh
./mvnw test                  # compilar y ejecutar las pruebas
./mvnw package               # generar el JAR en target/
java -jar target/administrador-de-inventario-1.0-SNAPSHOT.jar            # usa la carpeta ./data
java -jar target/administrador-de-inventario-1.0-SNAPSHOT.jar otra/ruta  # carpeta de datos alternativa
./mvnw exec:java             # ejecutar sin generar el JAR
```
