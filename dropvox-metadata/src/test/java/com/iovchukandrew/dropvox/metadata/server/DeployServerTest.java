package com.iovchukandrew.dropvox.metadata.server;

import com.iovchukandrew.dropvox.metadata.ConfigRetrieverFactory;
import com.iovchukandrew.dropvox.metadata.db.FilesDAO;
import com.iovchukandrew.dropvox.metadata.s3.S3PresignedUrlGenerator;
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
    FilesDAO filesDAO;
    @Mock
    S3PresignedUrlGenerator s3PresignedUrlGenerator;

    @Test
    void shouldDeployServer(Vertx vertx, VertxTestContext testContext) {
        var configRetriever = ConfigRetrieverFactory.create(vertx);
        configRetriever.getConfig()
                .onSuccess(config -> deployServer(vertx, testContext, config))
                .onFailure(testContext::failNow);
    }

    private void deployServer(Vertx vertx, VertxTestContext testContext, JsonObject config) {
        vertx.deployVerticle(new Server(filesDAO, s3PresignedUrlGenerator, config))
                .onComplete(handler -> {
                    if (handler.succeeded()) {
                        testContext.completeNow();
                    } else {
                        testContext.failNow(handler.cause());
                    }
                });
    }
}
