package com.duoc.cloud;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.Scalars;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RolesGraphQLFunction {

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
                    InputStream in = RolesGraphQLFunction.class.getClassLoader().getResourceAsStream("wallet/" + fileName);
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

    private static GraphQL graphQL;

    static {
        var rolType = GraphQLObjectType.newObject()
                .name("Rol")
                .field(field -> field.name("id").type(Scalars.GraphQLString))
                .field(field -> field.name("nombre").type(Scalars.GraphQLString))
                .field(field -> field.name("descripcion").type(Scalars.GraphQLString))
                .build();

        DataFetcher<List<Map<String, String>>> rolesDataFetcher = environment -> {
            List<Map<String, String>> rolesList = new ArrayList<>();
            String idFiltro = environment.getArgument("id");
            
            String walletPath = getWalletPath();
            java.util.Properties props = new java.util.Properties();
            props.setProperty("user", DB_USER);
            props.setProperty("password", DB_PASSWORD);
            props.setProperty("oracle.net.tns_admin", walletPath);
            props.setProperty("oracle.net.wallet_location", "(SOURCE=(METHOD=FILE)(METHOD_DATA=(DIRECTORY=" + walletPath + ")))");

            String sql = "SELECT ID_ROL, NOMBRE_ROL, DESCRIPCION FROM ROLES";
            if (idFiltro != null) {
                sql += " WHERE ID_ROL = ?";
            }

            try (Connection conn = DriverManager.getConnection(DB_URL, props);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                
                if (idFiltro != null) {
                    pstmt.setString(1, idFiltro);
                }
                
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, String> rol = new HashMap<>();
                        rol.put("id", rs.getString("ID_ROL"));
                        rol.put("nombre", rs.getString("NOMBRE_ROL"));
                        rol.put("descripcion", rs.getString("DESCRIPCION"));
                        rolesList.add(rol);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error ejecutando DataFetcher de Roles: " + e.getMessage());
            }
            return rolesList;
        };

        var queryType = GraphQLObjectType.newObject()
                .name("Query")
                .field(field -> field
                        .name("roles")
                        .type(new GraphQLList(rolType))
                        .argument(arg -> arg.name("id").type(Scalars.GraphQLString))
                        .dataFetcher(rolesDataFetcher))
                .build();

        var schema = GraphQLSchema.newSchema()
                .query(queryType)
                .build();

        graphQL = GraphQL.newGraphQL(schema).build();
    }

    @FunctionName("RolesGraphQLFunction")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req", 
                methods = {HttpMethod.POST}, 
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "graphql/roles") 
            HttpRequestMessage<Map<String, Object>> request,
            final ExecutionContext context) {

        String query = (String) request.getBody().get("query");
        
        if (query == null || query.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Falta el parámetro 'query' en el body JSON.")
                    .build();
        }

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(query).build();
        Map<String, Object> result = graphQL.execute(executionInput).toSpecification();

        return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(result)
                .build();
    }
}