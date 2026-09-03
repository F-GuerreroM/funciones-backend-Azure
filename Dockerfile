# Usamos una imagen oficial de OpenJDK 21
FROM eclipse-temurin:21-jdk-alpine

# Directorio de trabajo dentro del contenedor
WORKDIR /app

# Copiamos el archivo JAR empaquetado de Maven
COPY target/*.jar app.jar

# Exponemos el puerto 8080 donde corre Spring Boot
EXPOSE 8080

# Comando para ejecutar la aplicación al iniciar el contenedor
ENTRYPOINT ["java", "-jar", "app.jar"]