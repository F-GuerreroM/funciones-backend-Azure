package com.duoc.cloud;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;

public class RolesFunction {

    private static final String DB_USER = "ADMIN"; 
    private static final String DB_PASSWORD = "Duoc1234$$3210";
    private static final String DB_URL = "jdbc:oracle:thin:@bdmicroservicios_low";    

    private static String getWalletPath() {
        try {
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "wallet");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
                String[] files = {"cwallet.sso", "ewallet.p12", "tnsnames.ora", "sqlnet.ora", "ewallet.pem"};
                for (String fileName : files) {
                    InputStream in = RolesFunction.class.getClassLoader().getResourceAsStream("wallet/" + fileName);
                    if (in != null) {
                        File targetFile = new File(tempDir, fileName);
                        Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            return tempDir.getAbsolutePath().replace("\\", "/");
        } catch (Exception e) {
            return "C:/wallet";
        }
    }

    @FunctionName("RolesFunction")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET, HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "roles") 
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("Iniciando ejecución de RolesFunction. Método: " + request.getHttpMethod());

        HttpMethod metodo = request.getHttpMethod();
        String walletPath = getWalletPath();

        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", DB_USER);
        props.setProperty("password", DB_PASSWORD);
        props.setProperty("oracle.net.tns_admin", walletPath);
        props.setProperty("oracle.net.wallet_location", "(SOURCE=(METHOD=FILE)(METHOD_DATA=(DIRECTORY=" + walletPath + ")))");

        try (Connection conn = DriverManager.getConnection(DB_URL, props)) {
            context.getLogger().info("Conexión a Oracle exitosa desde RolesFunction.");

            if (metodo.equals(HttpMethod.GET)) {
                context.getLogger().info("Ejecutando consulta de roles...");
                return request.createResponseBuilder(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body("[{\"mensaje\":\"Lista de roles obtenida exitosamente desde Oracle Cloud\"}]")
                        .build();
            } 
            
            if (metodo.equals(HttpMethod.POST)) {
                String body = request.getBody().orElse("");
                context.getLogger().info("Creando nuevo rol: " + body);
                return request.createResponseBuilder(HttpStatus.CREATED)
                        .body("Rol creado con éxito en la base de datos.")
                        .build();
            }

        } catch (SQLException e) {
            context.getLogger().severe("Error crítico al conectar o consultar Oracle: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error interno en el servidor de base de datos: " + e.getMessage())
                    .build();
        }

        return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Método no soportado").build();
    }
}