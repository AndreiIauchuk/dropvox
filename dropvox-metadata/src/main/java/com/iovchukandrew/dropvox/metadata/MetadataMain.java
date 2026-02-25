package com.iovchukandrew.dropvox.metadata;

import com.iovchukandrew.dropvox.metadata.db.FilesDAO;
import com.iovchukandrew.dropvox.metadata.db.FlywayRunner;
import com.iovchukandrew.dropvox.metadata.db.PgPoolCreator;
import com.iovchukandrew.dropvox.metadata.s3.S3PresignedUrlGenerator;
import com.iovchukandrew.dropvox.metadata.server.Server;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MetadataMain {
    private static final Logger log = LoggerFactory.getLogger(MetadataMain.class);

    public static void main(String[] args) {
        configureLogging();
        log.info("Application started");

        Vertx vertx = Vertx.vertx();

        ConfigRetriever configRetriever = createConfigRetriever(vertx);

        configRetriever.getConfig()
                .compose(config -> {
                    log.info("Configuration loaded successfully");
                    parseEnvVars(config);
                    return runFlywayMigration(vertx, config).map(r -> config);
                })
                .compose(config -> startApplication(vertx, config))
                .onSuccess(ctx -> setupShutdownHook(vertx, ctx))
                .onFailure(e -> {
                    log.error("Failed to start an application", e);
                    AppContext.closeVertx(vertx);
                });
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

        //env vars will overwrite properties from file
        ConfigStoreOptions envStore = new ConfigStoreOptions()
                .setType("env")
                .setConfig(new JsonObject());

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

    private static Future<MigrateResult> runFlywayMigration(Vertx vertx, JsonObject config) {
        return vertx.executeBlocking(() -> new FlywayRunner().runMigration(config));
    }

    private static Future<AppContext> startApplication(Vertx vertx, JsonObject config) {
        var sqlPool = PgPoolCreator.create(vertx, config);
        var s3Presigner = createS3Presigner(config);
        var s3PresignedUrlGenerator = new S3PresignedUrlGenerator(s3Presigner);
        var metadataDAO = new FilesDAO(sqlPool);

        return deployServer(vertx, metadataDAO, s3PresignedUrlGenerator, config)
                .map(id -> new AppContext(sqlPool, s3Presigner));
    }

    private static void setupShutdownHook(Vertx vertx, AppContext ctx) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                ctx.closeAllResources(vertx).await(10, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new RuntimeException("Unable to close all resources");
            }
        }));
    }

    private static Future<String> deployServer(
            Vertx vertx,
            FilesDAO filesDAO,
            S3PresignedUrlGenerator s3PresignedUrlGenerator,
            JsonObject config
    ) {
        return vertx.deployVerticle(new Server(filesDAO, s3PresignedUrlGenerator, config))
                .onSuccess(id -> log.info("Verticle deployed [id:{}]", id));
    }

    private static S3Presigner createS3Presigner(JsonObject config) {
        String presignEndpoint = config.getString("s3.publicEndpoint", config.getString("s3.endpoint")); //FIXME Temporary solution

        return S3Presigner.builder()
                .endpointOverride(URI.create(presignEndpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                config.getString("s3.accessKey"), config.getString("s3.secretKey"))))
                .region(Region.of(config.getString("s3.region", "us-east-1")))
                .build();
    }

    private static void configureLogging() {
        System.setProperty("vertx.logger-delegate-factory-class-name",
                "io.vertx.core.logging.SLF4JLogDelegateFactory");
    }
}
