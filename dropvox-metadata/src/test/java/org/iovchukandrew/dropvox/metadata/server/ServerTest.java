package org.iovchukandrew.dropvox.metadata.server;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.iovchukandrew.dropvox.metadata.db.MetadataDAO;
import org.iovchukandrew.dropvox.metadata.s3.S3PresignedUrlGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;

@ExtendWith(VertxExtension.class)
public class ServerTest {

    @Mock
    MetadataDAO metadataDAO;
    @Mock
    S3PresignedUrlGenerator s3PresignedUrlGenerator;

    @Test
    void shouldDeployVerticle(Vertx vertx, VertxTestContext testContext) {
        vertx.deployVerticle(new Server(metadataDAO, s3PresignedUrlGenerator))
                .onComplete(handler -> {
                    if (handler.succeeded()) {
                        testContext.completeNow();
                    } else {
                        testContext.failNow(handler.cause());
                    }
                });
    }
}
