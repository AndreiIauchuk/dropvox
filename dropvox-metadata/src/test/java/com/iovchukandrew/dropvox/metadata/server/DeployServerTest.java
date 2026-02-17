package com.iovchukandrew.dropvox.metadata.server;

import com.iovchukandrew.dropvox.metadata.db.MetadataDAO;
import com.iovchukandrew.dropvox.metadata.s3.S3PresignedUrlGenerator;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;

@ExtendWith(VertxExtension.class)
public class DeployServerTest {

    @Mock
    MetadataDAO metadataDAO;
    @Mock
    S3PresignedUrlGenerator s3PresignedUrlGenerator;

    @Test
    void shouldDeployServer(Vertx vertx, VertxTestContext testContext) {
        var configRetriever = createConfigRetriever(vertx);
        configRetriever.getConfig()
                .onSuccess(config -> deployServer(vertx, testContext, config))
                .onFailure(testContext::failNow);
    }

    private void deployServer(Vertx vertx, VertxTestContext testContext, JsonObject config) {
        vertx.deployVerticle(new Server(metadataDAO, s3PresignedUrlGenerator, config))
                .onComplete(handler -> {
                    if (handler.succeeded()) {
                        testContext.completeNow();
                    } else {
                        testContext.failNow(handler.cause());
                    }
                });
    }

    private ConfigRetriever createConfigRetriever(Vertx vertx) {
        ConfigStoreOptions fileStore = new ConfigStoreOptions()
                .setType("file")
                .setFormat("properties")
                .setConfig(new JsonObject().put("path", "application.properties"));

        ConfigStoreOptions envStore = new ConfigStoreOptions()
                .setType("env")
                .setConfig(new JsonObject().put("raw-data", true)); //env vars will overwrite properties from file

        return ConfigRetriever.create(vertx,
                new ConfigRetrieverOptions().addStore(fileStore).addStore(envStore));
    }
}
