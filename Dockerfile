# Versión web en un contenedor: sirve para publicarla en Render, Railway, Fly.io o cualquier servidor con Docker.
#   docker build -t inventario .
#   docker run -p 7070:7070 -v inventario-datos:/datos inventario
# La base SQLite queda en el volumen /datos: sin volumen, los datos se pierden al recrear el contenedor.

# 1) Compilación (con pruebas) usando el Maven Wrapper del proyecto
FROM eclipse-temurin:26-jdk AS compilacion
WORKDIR /fuente
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package

# 2) Imagen final: solo el runtime de Java y el JAR
FROM eclipse-temurin:26-jre
WORKDIR /app
RUN useradd --system --home /app inventario && mkdir /datos && chown inventario /datos
COPY --from=compilacion /fuente/target/administrador-de-inventario-1.0-SNAPSHOT.jar app.jar
USER inventario
VOLUME /datos
ENV PORT=7070
EXPOSE 7070
# Agregue "--demo" al final para probar con datos de ejemplo.
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar", "--web", "/datos"]
