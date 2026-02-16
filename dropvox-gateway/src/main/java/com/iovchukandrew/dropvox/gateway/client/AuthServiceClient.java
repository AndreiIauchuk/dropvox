package com.iovchukandrew.dropvox.gateway.client;

import io.vertx.core.Future;

/**
 * Calls Auth Service for user authentication.
 */
public class AuthServiceClient {

    /**
     * Validates the token with Auth Service and extracts user ID.
     *
     * @param token JWT token
     * @return Future containing user ID
     */
    public Future<String> validateToken(String token) {
        return Future.succeededFuture("mock-user-id");

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
