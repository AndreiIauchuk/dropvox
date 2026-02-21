package com.iovchukandrew.dropvox.metadata;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

public final class AppContext {
    private static final Logger log = LoggerFactory.getLogger(AppContext.class);

    private final Pool sqlPool;
    private final S3Presigner s3Presigner;

    AppContext(Pool sqlPool, S3Presigner s3Presigner) {
        this.sqlPool = sqlPool;
        this.s3Presigner = s3Presigner;
    }

    public CompositeFuture closeAllResources(Vertx vertx) {
        Future<Void> sqlClose = closePgPool(sqlPool);
        Future<Void> s3Close = vertx.executeBlocking(() -> {
            closeS3Presigner(s3Presigner);
            return null;
        }).mapEmpty();

        return Future.all(sqlClose, s3Close)
                .onComplete(v -> closeVertx(vertx));
    }

    private static Future<Void> closePgPool(Pool sqlPool) {
        log.info("Closing SQLPool...");
        return sqlPool.close()
                .onSuccess(e -> log.info("SQLPool was closed successfully"))
                .onFailure(e -> log.error("Error closing SQLPool", e));
    }

    private static void closeS3Presigner(S3Presigner s3Presigner) {
        log.info("Closing S3Presigner...");

        try {
            s3Presigner.close();
            log.info("S3Presigner was closed successfully");
        } catch (Exception e) {
            log.error("Error closing S3Presigner", e);
        }
    }

    public static Future<Void> closeVertx(Vertx vertx) {
        log.info("Closing Vertx...");
        return vertx.close()
                .onSuccess(e -> log.info("Vertx was closed successfully"))
                .onFailure(e -> log.error("Error closing Vertx", e));
    }
}