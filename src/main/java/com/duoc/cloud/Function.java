package com.duoc.cloud;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Function {

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
                    InputStream in = Function.class.getClassLoader().getResourceAsStream("wallet/" + fileName);
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

    // Función Helper para extraer datos del JSON sin usar librerías externas
    private String extraerValorJson(String json, String key) {
        if (json == null || json.isEmpty()) return null;
        Matcher matcher = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([^\"},]+)\"?").matcher(json);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    @FunctionName("UsuariosFunction")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "usuarios") 
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        String walletPath = getWalletPath();
        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", DB_USER);
        props.setProperty("password", DB_PASSWORD);
        props.setProperty("oracle.net.tns_admin", walletPath);
        props.setProperty("oracle.net.wallet_location", "(SOURCE=(METHOD=FILE)(METHOD_DATA=(DIRECTORY=" + walletPath + ")))");

        try (Connection conn = DriverManager.getConnection(DB_URL, props)) {
            
            switch (request.getHttpMethod()) {
                case GET:
                    return handleGet(request, conn);
                case POST:
                    return handlePost(request, conn);
                case PUT:
                    return handlePut(request, conn);
                case DELETE:
                    return handleDelete(request, conn);
                default:
                    return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Método no soportado").build();
            }

        } catch (SQLException e) {
            context.getLogger().severe("Error BD: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error BD: " + e.getMessage()).build();
        }
    }

    // ==========================================
    //            MÉTODOS DEL CRUD
    // ==========================================

    private HttpResponseMessage handleGet(HttpRequestMessage<Optional<String>> request, Connection conn) throws SQLException {
        StringBuilder jsonResult = new StringBuilder("[");
        String sql = "SELECT ID_USUARIO, NOMBRE, CORREO, ID_ROL FROM USUARIOS";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            boolean first = true;
            while (rs.next()) {
                if (!first) jsonResult.append(",");
                jsonResult.append("{")
                          .append("\"id_usuario\":\"").append(rs.getString("ID_USUARIO")).append("\",")
                          .append("\"nombre\":\"").append(rs.getString("NOMBRE")).append("\",")
                          .append("\"correo\":\"").append(rs.getString("CORREO")).append("\",")
                          .append("\"id_rol\":\"").append(rs.getString("ID_ROL")).append("\"")
                          .append("}");
                first = false;
            }
            jsonResult.append("]");
            
            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(jsonResult.toString())
                    .build();
        }
    }

   private HttpResponseMessage handlePost(HttpRequestMessage<Optional<String>> request, Connection conn) throws SQLException {
        String body = request.getBody().orElse("");
        String nombre = extraerValorJson(body, "nombre");
        String correo = extraerValorJson(body, "correo");
        String idRol = extraerValorJson(body, "id_rol");
        if (idRol == null) idRol = "1"; // Rol por defecto
        
        // CORRECCIÓN: Le quitamos el ID_USUARIO para que Oracle lo autogenere
        String sql = "INSERT INTO USUARIOS (NOMBRE, CORREO, ID_ROL) VALUES (?, ?, ?)";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nombre);
            pstmt.setString(2, correo);
            pstmt.setString(3, idRol);
            pstmt.executeUpdate();
            
            return request.createResponseBuilder(HttpStatus.CREATED)
                    .header("Content-Type", "application/json")
                    .body("{\"mensaje\":\"Usuario creado exitosamente\"}").build();
        }
    }

    private HttpResponseMessage handlePut(HttpRequestMessage<Optional<String>> request, Connection conn) throws SQLException {
        String body = request.getBody().orElse("");
        String idUsuario = extraerValorJson(body, "id_usuario");
        if (idUsuario == null) idUsuario = extraerValorJson(body, "id");
        
        String nombre = extraerValorJson(body, "nombre");
        String correo = extraerValorJson(body, "correo");
        
        String sql = "UPDATE USUARIOS SET NOMBRE = ?, CORREO = ? WHERE ID_USUARIO = ?";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nombre);
            pstmt.setString(2, correo);
            pstmt.setString(3, idUsuario);
            int rows = pstmt.executeUpdate();
            
            if (rows > 0) {
                return request.createResponseBuilder(HttpStatus.OK).body("{\"mensaje\":\"Usuario actualizado\"}").build();
            } else {
                return request.createResponseBuilder(HttpStatus.NOT_FOUND).body("{\"mensaje\":\"Usuario no encontrado\"}").build();
            }
        }
    }

    private HttpResponseMessage handleDelete(HttpRequestMessage<Optional<String>> request, Connection conn) throws SQLException {
        String body = request.getBody().orElse("");
        String idUsuario = extraerValorJson(body, "id_usuario");
        if (idUsuario == null) idUsuario = extraerValorJson(body, "id");
        
        if (idUsuario == null) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("{\"mensaje\":\"Falta el ID del usuario\"}").build();
        }
        
        String sql = "DELETE FROM USUARIOS WHERE ID_USUARIO = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, idUsuario);
            pstmt.executeUpdate();
            return request.createResponseBuilder(HttpStatus.OK).body("{\"mensaje\":\"Usuario eliminado\"}").build();
        }
    }
}