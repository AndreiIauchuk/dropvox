package com.iovchukandrew.dropvox.metadata;

import com.iovchukandrew.dropvox.metadata.db.FilesDAO;
import com.iovchukandrew.dropvox.metadata.db.FlywayRunner;
import com.iovchukandrew.dropvox.metadata.db.PgPoolCreator;
import com.iovchukandrew.dropvox.metadata.s3.S3PresignedUrlGenerator;
import com.iovchukandrew.dropvox.metadata.server.Server;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MetadataMain {
    private static final Logger log = LoggerFactory.getLogger(MetadataMain.class);

    public static void main(String[] args) {
        configureLogging();

        Vertx vertx = Vertx.vertx();

        ConfigRetriever configRetriever = createConfigRetriever(vertx);

        CountDownLatch shutdownLatch = new CountDownLatch(1);
        configRetriever.getConfig()
                .onSuccess(config -> {
                    log.info("Configuration loaded successfully");
                    parseEnvVars(config);
                    new FlywayRunner().runMigration(config);
                    startApplication(vertx, config, shutdownLatch);
                })
                .onFailure(e -> {
                    log.error("Failed to load configuration", e);
                    closeVertx(vertx);
                    shutdownLatch.countDown();
                    System.exit(1);
                });

        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static ConfigRetriever createConfigRetriever(Vertx vertx) {
        String propertiesFile = System.getenv("PROP_FILE");
        if (propertiesFile == null) {
            propertiesFile = "application.properties";
        }

        ConfigStoreOptions fileStore = new ConfigStoreOptions()
                .setType("file")
                .setFormat("properties")
                .setConfig(new JsonObject().put("path", propertiesFile));

        ConfigStoreOptions envStore = new ConfigStoreOptions()
                .setType("env")
                .setConfig(new JsonObject()); //env vars will overwrite properties from file

        return ConfigRetriever.create(
                vertx,
                new ConfigRetrieverOptions()
                        .addStore(fileStore)
                        .addStore(envStore));
    }

    private static void parseEnvVars(JsonObject config) {
        Map<String, Object> updates = new HashMap<>();
        config.fieldNames()
                .forEach(envKey -> {
                    String configKey = envKey.toLowerCase().replaceAll("_", ".");
                    updates.put(configKey, config.getValue(envKey));
                });

        updates.forEach(config::put);
    }

    private static void startApplication(Vertx vertx, JsonObject config, CountDownLatch shutdownLatch) {
        var sqlPool = PgPoolCreator.create(vertx, config);
        var metadataDAO = new FilesDAO(vertx, sqlPool);
        var s3Presigner = createS3Presigner(config);
        var s3PresignedUrlGenerator = new S3PresignedUrlGenerator(s3Presigner);

        setupShutdownHook(vertx, sqlPool, s3Presigner, shutdownLatch);

        deployServer(vertx, metadataDAO, s3PresignedUrlGenerator, sqlPool, s3Presigner, shutdownLatch, config);
    }

    private static void setupShutdownHook(
            Vertx vertx, Pool sqlPool, S3Presigner s3Presigner, CountDownLatch shutdownLatch) {
        Runtime.getRuntime().addShutdownHook(
                new Thread(
                        () -> {
                            closePgPool(sqlPool);
                            closeS3Presigner(s3Presigner);
                            closeVertx(vertx);
                            shutdownLatch.countDown();
                        }
                )
        );
    }

    private static void deployServer(
            Vertx vertx,
            FilesDAO filesDAO,
            S3PresignedUrlGenerator s3PresignedUrlGenerator,
            Pool sqlPool,
            S3Presigner s3Presigner,
            CountDownLatch shutdownLatch,
            JsonObject config
    ) {
        vertx.deployVerticle(new Server(filesDAO, s3PresignedUrlGenerator, config))
                .onSuccess(id -> log.info("Verticle deployed, id: {}", id))
                .onFailure(e -> {
                    log.error("Failed to deploy verticle", e);
                    closePgPool(sqlPool);
                    closeS3Presigner(s3Presigner);
                    closeVertx(vertx);
                    shutdownLatch.countDown();
                });
    }

    private static S3Presigner createS3Presigner(JsonObject config) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(config.getString("s3.endpoint")))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                config.getString("s3.accessKey"), config.getString("s3.secretKey")))
                )
                .region(Region.of(config.getString("s3.region", "us-east-1")))
                .build();
    }

    private static void closePgPool(Pool sqlPool) {
        log.info("Closing SQLPool...");
        try {
            sqlPool.close()
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            log.info("SQLPool closed");
        } catch (Exception e) {
            log.error("Error closing SQLPool", e);
        }
    }

    private static void closeS3Presigner(S3Presigner s3Presigner) {
        log.info("Closing S3Presigner...");
        try {
            s3Presigner.close();
            log.info("S3Presigner is closed");
        } catch (Exception e) {
            log.error("Error closing S3Presigner", e);
        }
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
            log.error("Error closing Vertx", e);
        }
    }

    private static void configureLogging() {
        System.setProperty("vertx.logger-delegate-factory-class-name",
                "io.vertx.core.logging.SLF4JLogDelegateFactory");
    }
}