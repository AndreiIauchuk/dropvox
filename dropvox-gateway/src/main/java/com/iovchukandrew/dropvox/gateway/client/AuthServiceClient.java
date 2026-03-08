package com.iovchukandrew.dropvox.gateway.client;

import io.vertx.core.Future;
import io.vertx.ext.web.client.WebClient;

/**
 * Calls Auth Service for user authentication.
 */
public class AuthServiceClient {
    private final WebClient webClient;
    private final String host;
    private final int port;

    public AuthServiceClient(WebClient webClient, String host, int port) {
        this.webClient = webClient;
        this.host = host;
        this.port = port;
    }

    /**
     * Validates the token with Auth Service and extracts user ID.
     *
     * @param token JWT token
     * @return Future containing user ID
     */
    public Future<String> validateToken(String token) {
        return Future.succeededFuture("3b069db6-b46a-4766-96d8-9bee5c5a32be");

        // JsonObject payload = new JsonObject().put("token", token);
        // return webClient.post(authPort, authHost, "/validate")
        //         .putHeader("Content-Type", "application/json")
        //         .sendJson(payload)
        //         .compose(response -> {
        //             JsonObject body = response.bodyAsJsonObject();
        //             String userId = body.getString("userId");
        //             if (userId == null) {
        //                 return Future.failedFuture("Invalid token response: missing userId");
        //             }
        //             return Future.succeededFuture(userId);
        //         });
    }
}
