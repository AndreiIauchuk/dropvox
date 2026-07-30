package com.iovchukandrew.dropvox.metadata.server;

import com.iovchukandrew.dropvox.metadata.db.FilesDAO;
import com.iovchukandrew.dropvox.metadata.kafka.FileUploadCompletionRequestedConsumer;
import com.iovchukandrew.dropvox.metadata.kafka.MinioObjectCreatedConsumer;
import com.iovchukandrew.dropvox.metadata.processing.UploadCompletionProcessor;
import com.iovchukandrew.dropvox.metadata.s3.S3ObjectExistenceChecker;
import com.iovchukandrew.dropvox.metadata.s3.S3PresignedUrlGenerator;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.micrometer.PrometheusScrapingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class Server extends VerticleBase {
    private static final Logger log = LoggerFactory.getLogger(Server.class);

    private final FilesDAO filesDAO;
    private final S3PresignedUrlGenerator s3PresignedUrlGenerator;
    private final S3ObjectExistenceChecker s3ObjectExistenceChecker;
    private final JsonObject config;
    private FileUploadCompletionRequestedConsumer fileUploadCompletionRequestedConsumer;
    private MinioObjectCreatedConsumer minioObjectCreatedConsumer;

    public Server(
            FilesDAO filesDAO,
            S3PresignedUrlGenerator s3PresignedUrlGenerator,
            S3ObjectExistenceChecker s3ObjectExistenceChecker,
            JsonObject config
    ) {
        this.filesDAO = filesDAO;
        this.s3PresignedUrlGenerator = s3PresignedUrlGenerator;
        this.s3ObjectExistenceChecker = s3ObjectExistenceChecker;
        this.config = config;
    }

    @Override
    public Future<HttpServer> start() {
        UploadCompletionProcessor uploadCompletionProcessor = new UploadCompletionProcessor(
                vertx,
                filesDAO,
                s3ObjectExistenceChecker,
                config.getInteger("upload.completion.max.s3.existence.check.attempts", 5),
                config.getLong("upload.completion.s3.existence.retry.delay.ms", 200L)
        );

        Future<Void> kafkaStartup = startKafkaConsumers(uploadCompletionProcessor);
        Router router = Router.router(vertx);
        router.route().handler(this::traceIdMiddleware);
        router.get("/health/live").handler(ctx -> ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("status", "UP").encode()));
        router.get("/metrics").handler(PrometheusScrapingHandler.create());
        router.route().handler(BodyHandler.create());

        String bucketName = config.getString("s3.bucket");

        router.get("/files/:fileId")
                .handler(new FileDownloadHandler(filesDAO, s3PresignedUrlGenerator));
        router.get("/files/:fileId/status")
                .handler(new FileUploadStatusHandler(filesDAO));
        router.post("/files/init")
                .handler(new FileUploadInitHandler(filesDAO, s3PresignedUrlGenerator, bucketName));
        router.post("/files/:fileId/completion-request")
                .handler(new FileUploadCompleteHandler(uploadCompletionProcessor));

        HttpServerOptions serverOptions = new HttpServerOptions().setHttp2ClearTextEnabled(false);
        int port = config.getInteger("server.port");
        return kafkaStartup.compose(ignored -> vertx.createHttpServer(serverOptions)
                .requestHandler(router)
                .listen(port))
                .onSuccess(s -> log.info("Server started on {} port", port))
                .onFailure(e -> log.error("Failed to deploy Server verticle on port {}", port, e));
    }

    @Override
    public Future<?> stop() {
        List<Future<?>> closeFutures = new ArrayList<>();
        if (fileUploadCompletionRequestedConsumer != null) {
            closeFutures.add(fileUploadCompletionRequestedConsumer.close());
        }
        if (minioObjectCreatedConsumer != null) {
            closeFutures.add(minioObjectCreatedConsumer.close());
        }

        if (closeFutures.isEmpty()) {
            return Future.succeededFuture();
        }

        return Future.all(closeFutures).mapEmpty();
    }

    private Future<Void> startKafkaConsumers(UploadCompletionProcessor uploadCompletionProcessor) {
        if (!config.getBoolean("kafka.enabled", false)) {
            return Future.succeededFuture();
        }

        fileUploadCompletionRequestedConsumer = FileUploadCompletionRequestedConsumer.create(
                vertx,
                config,
                uploadCompletionProcessor
        );

        List<Future<?>> startupFutures = new ArrayList<>();
        startupFutures.add(fileUploadCompletionRequestedConsumer.start());

        if (config.getBoolean("minio.kafka.notifications.enabled", false)) {
            minioObjectCreatedConsumer = MinioObjectCreatedConsumer.create(vertx, config, uploadCompletionProcessor);
            startupFutures.add(minioObjectCreatedConsumer.start());
        }

        return Future.all(startupFutures).mapEmpty();
    }

    private void traceIdMiddleware(RoutingContext ctx) {
        String traceId = Optional.ofNullable(ctx.request().getHeader(HttpHeader.TRACE_ID))
                .orElse(UUID.randomUUID().toString());

        MDC.put("traceId", traceId);
        ctx.response().putHeader(HttpHeader.TRACE_ID, traceId);
        ctx.put("traceId", traceId);

        ctx.addEndHandler(v -> MDC.remove("traceId"));
        ctx.next();
    }
}
