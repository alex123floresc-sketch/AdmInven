# Administrador de inventario

Aplicación de escritorio en Java (JDK 26, JavaFX, SQLite, Maven) para gestionar productos y su stock.

## Funcionalidades

- Interfaz gráfica con pestañas de Resumen, Productos y Movimientos; menú de consola como alternativa.
- Alta, edición, baja lógica, reactivación y búsqueda de productos (código, nombre, categoría, precio, stock mínimo).
- Entradas, salidas y ajustes de stock por conteo físico, con validación de stock suficiente.
- Historial de movimientos por producto y registro completo filtrable.
- Alertas de productos en o por debajo del stock mínimo (resaltados en rojo).
- Resumen: productos activos, unidades totales, valor del inventario y últimos movimientos.
- Base de datos SQLite en `data/inventario.db`, con migraciones de esquema automáticas y transacciones.
- Importación automática de los datos CSV de versiones anteriores (`data/productos.csv`, `data/movimientos.csv`).
- Datos de ejemplo para demostraciones (`--demo`): un minimarket con 60 días de actividad.

## Estructura

```
src/main/java/inventario/
├── Main.java, Opciones.java  Punto de entrada y argumentos
├── Aplicacion.java           Arma la aplicación: base de datos, importación y servicios
├── modelo/                   Producto, Movimiento, TipoMovimiento
├── persistencia/             Repositorios: interfaces, CSV (importación) y sqlite/ (almacenamiento)
├── servicio/                 Reglas de negocio (InventarioServicio)
└── ui/                       Menú de consola y fx/ (interfaz JavaFX)
```

La interfaz solo habla con `InventarioServicio`, y este solo con las interfaces de repositorio,
así que la UI y el almacenamiento se pueden cambiar sin tocar las reglas de negocio.

## Ejecutar

Solo se necesita el JDK 26: el Maven Wrapper (`mvnw`) descarga Maven y las librerías la primera vez.

Desde IntelliJ: abrir la carpeta (se importa como proyecto Maven) y ejecutar `inventario.Main`.

Desde la terminal (en PowerShell usar `.\mvnw.cmd` en lugar de `./mvnw`):

```sh
./mvnw test                  # compilar y ejecutar las pruebas
./mvnw package               # generar el JAR ejecutable en target/
java -jar target/administrador-de-inventario-1.0-SNAPSHOT.jar                # ventana, datos en ./data
java -jar target/administrador-de-inventario-1.0-SNAPSHOT.jar --demo         # ventana con datos de ejemplo en ./demo
java -jar target/administrador-de-inventario-1.0-SNAPSHOT.jar --consola      # menú de texto
java -jar target/administrador-de-inventario-1.0-SNAPSHOT.jar otra/carpeta   # carpeta de datos alternativa
./mvnw exec:exec             # ejecutar sin generar el JAR
```
