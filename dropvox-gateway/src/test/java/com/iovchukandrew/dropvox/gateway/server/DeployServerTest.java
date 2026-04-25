package com.iovchukandrew.dropvox.gateway.server;

import com.iovchukandrew.dropvox.gateway.ConfigRetrieverFactory;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class DeployServerTest {

    @Test
    void shouldDeployServer(Vertx vertx, VertxTestContext testContext) {
        var configRetriever = ConfigRetrieverFactory.create(vertx);
        configRetriever.getConfig()
                .onSuccess(config -> deployServer(vertx, testContext, config))
                .onFailure(testContext::failNow);
    }

    @Test
    void shouldValidatePathParametersUsingOpenApi(Vertx vertx, VertxTestContext testContext) {
        var configRetriever = ConfigRetrieverFactory.create(vertx);
        configRetriever.getConfig()
                .onSuccess(config -> deployServerAndVerifyInvalidFileId(vertx, testContext, config))
                .onFailure(testContext::failNow);
    }

    @Test
    void shouldServeOpenApiDocumentFromDocsEndpoint(Vertx vertx, VertxTestContext testContext) {
        var configRetriever = ConfigRetrieverFactory.create(vertx);
        configRetriever.getConfig()
                .onSuccess(config -> deployServerAndVerifyDocs(vertx, testContext, config))
                .onFailure(testContext::failNow);
    }

    @Test
    void shouldServeSwaggerUiAssetsLocally(Vertx vertx, VertxTestContext testContext) {
        var configRetriever = ConfigRetrieverFactory.create(vertx);
        configRetriever.getConfig()
                .onSuccess(config -> deployServerAndVerifySwaggerUiAsset(vertx, testContext, config))
                .onFailure(testContext::failNow);
    }

    private void deployServer(Vertx vertx, VertxTestContext testContext, JsonObject config) {
        WebClient webClient = WebClient.create(vertx);
        vertx.deployVerticle(new Server(webClient, config))
                .onComplete(handler -> {
                    if (handler.succeeded()) {
                        testContext.completeNow();
                    } else {
                        testContext.failNow(handler.cause());
                    }
                });
    }

    private void deployServerAndVerifyInvalidFileId(Vertx vertx, VertxTestContext testContext, JsonObject config) {
        WebClient webClient = WebClient.create(vertx);
        vertx.deployVerticle(new Server(webClient, config))
                .compose(ignored -> webClient.get(config.getInteger("server.port"), "localhost", "/files/not-a-uuid")
                        .send())
                .onSuccess(response -> testContext.verify(() -> {
                    assertThat(response.statusCode()).isEqualTo(400);
                    assertThat(response.bodyAsString()).contains("invalid");
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

    private void deployServerAndVerifyDocs(Vertx vertx, VertxTestContext testContext, JsonObject config) {
        WebClient webClient = WebClient.create(vertx);
        vertx.deployVerticle(new Server(webClient, config))
                .compose(ignored -> webClient.get(config.getInteger("server.port"), "localhost", "/docs/openapi.json")
                        .send())
                .onSuccess(response -> testContext.verify(() -> {
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.getHeader("Content-Type")).contains("application/json");
                    assertThat(response.bodyAsJsonObject().getString("openapi")).isEqualTo("3.1.0");
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

    private void deployServerAndVerifySwaggerUiAsset(Vertx vertx, VertxTestContext testContext, JsonObject config) {
        WebClient webClient = WebClient.create(vertx);
        vertx.deployVerticle(new Server(webClient, config))
                .compose(ignored -> webClient.get(config.getInteger("server.port"), "localhost", "/docs/swagger-ui/swagger-ui.css")
                        .send())
                .onSuccess(response -> testContext.verify(() -> {
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.getHeader("Content-Type")).contains("text/css");
                    assertThat(response.bodyAsString()).contains(".swagger-ui");
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }
}
