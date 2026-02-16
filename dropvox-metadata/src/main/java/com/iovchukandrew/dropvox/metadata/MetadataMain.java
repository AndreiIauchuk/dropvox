package com.iovchukandrew.dropvox.metadata;

import com.iovchukandrew.dropvox.metadata.db.MetadataDAO;
import com.iovchukandrew.dropvox.metadata.db.PgPoolCreator;
import com.iovchukandrew.dropvox.metadata.s3.S3PresignedUrlGenerator;
import com.iovchukandrew.dropvox.metadata.server.Server;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MetadataMain {
    private static final Logger log = LoggerFactory.getLogger(MetadataMain.class);

    public static void main(String[] args) {
        configLogging();

        CountDownLatch shutdownLatch = new CountDownLatch(1);

        Vertx vertx = Vertx.vertx();

        var sqlPool = PgPoolCreator.create(vertx);
        var metadataDAO = new MetadataDAO(vertx, sqlPool);

        S3Presigner s3Presigner = createS3Presigner();
        var s3PresignedUrlGenerator = createS3PresignedUrlGenerator(s3Presigner);

        startServer(vertx, metadataDAO, s3PresignedUrlGenerator, sqlPool, s3Presigner, shutdownLatch);

        Runtime.getRuntime().addShutdownHook(
                new Thread(
                        () -> {
                            closeSqlPool(sqlPool);
                            closeS3Presigner(s3Presigner);
                            closeVertx(vertx);
                            shutdownLatch.countDown();
                        }
                )
        );

        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void startServer(
            Vertx vertx,
            MetadataDAO metadataDAO,
            S3PresignedUrlGenerator s3PresignedUrlGenerator,
            Pool sqlPool,
            S3Presigner s3Presigner,
            CountDownLatch shutdownLatch
    ) {
        vertx.deployVerticle(new Server(metadataDAO, s3PresignedUrlGenerator))
                .onSuccess(id -> log.info("Verticle deployed, id: {}", id))
                .onFailure(err -> {
                    log.error("Failed to deploy verticle: {}", err.getMessage());
                    closeSqlPool(sqlPool);
                    closeS3Presigner(s3Presigner);
                    closeVertx(vertx);
                    shutdownLatch.countDown();
                });
    }

    private static S3PresignedUrlGenerator createS3PresignedUrlGenerator(S3Presigner s3Presigner) {
        return new S3PresignedUrlGenerator(s3Presigner, "bucketName", Duration.ofMinutes(5));
    }

    private static S3Presigner createS3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create("http://minio:9000"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("minioadmin", "minioadmin")))
                .region(Region.US_EAST_1)
                .build();
    }

    private static void closeSqlPool(Pool sqlPool) {
        log.info("Closing SQLPool...");
        try {
            sqlPool.close()
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            log.info("SQLPool closed");
        } catch (Exception e) {
            log.error("Error closing SQLPool: {}", e.getMessage());
        }
    }

    private static void closeS3Presigner(S3Presigner s3Presigner) {
        log.info("Closing S3Presigner...");
        s3Presigner.close();
        log.info("S3Presigner is closed");
    }

    private static void closeVertx(Vertx vertx) {
        log.info("Closing Vertx...");
        try {
            vertx.close()
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
            log.info("Vertx is closed");
        } catch (Exception e) {
            log.error("Error closing Vertx: " + e.getMessage());
        }
    }

    private static void configLogging() {
        System.setProperty("vertx.logger-delegate-factory-class-name",
                "io.vertx.core.logging.SLF4JLogDelegateFactory");
    }
}