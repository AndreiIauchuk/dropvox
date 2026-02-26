package com.iovchukandrew.dropvox.metadata;

import com.iovchukandrew.dropvox.metadata.db.FilesDAO;
import com.iovchukandrew.dropvox.metadata.db.FlywayRunner;
import com.iovchukandrew.dropvox.metadata.db.PgPoolCreator;
import com.iovchukandrew.dropvox.metadata.s3.S3PresignedUrlGenerator;
import com.iovchukandrew.dropvox.metadata.s3.S3PresignerFactory;
import com.iovchukandrew.dropvox.metadata.server.Server;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MetadataMain {
    private static final Logger log = LoggerFactory.getLogger(MetadataMain.class);

    public static void main(String[] args) {
        configureLogging();
        log.info("Application started");

        Vertx vertx = Vertx.vertx();

        var configRetriever = ConfigRetrieverFactory.create(vertx);

        configRetriever.getConfig()
                .compose(MetadataMain::prepareConfig)
                .compose(config -> runMigrations(vertx, config))
                .compose(config -> startApplication(vertx, config))
                .onSuccess(ctx -> setupShutdownHook(vertx, ctx))
                .onFailure(e -> {
                    log.error("Failed to start an application", e);
                    AppContext.closeVertx(vertx);
                });
    }

    private static Future<JsonObject> prepareConfig(JsonObject config) {
        parseEnvVars(config);
        return Future.succeededFuture(config);
    }

    private static Future<JsonObject> runMigrations(Vertx vertx, JsonObject config) {
        return runFlywayMigration(vertx, config).map(r -> config);
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
        var s3Presigner = S3PresignerFactory.create(config);
        var s3PresignedUrlGenerator = new S3PresignedUrlGenerator(s3Presigner);
        var metadataDAO = new FilesDAO(sqlPool);

        return deployServer(vertx, metadataDAO, s3PresignedUrlGenerator, config)
                .map(id -> new AppContext(sqlPool, s3Presigner));
    }

    private static void setupShutdownHook(Vertx vertx, AppContext ctx) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                ctx.closeAllResources(vertx).await(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Unable to close all resources during shutdown", e);
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

    private static void configureLogging() {
        System.setProperty("vertx.logger-delegate-factory-class-name",
                "io.vertx.core.logging.SLF4JLogDelegateFactory");
    }
}
