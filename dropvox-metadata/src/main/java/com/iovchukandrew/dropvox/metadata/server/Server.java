package com.iovchukandrew.dropvox.metadata.server;

import com.iovchukandrew.dropvox.metadata.db.FilesDAO;
import com.iovchukandrew.dropvox.metadata.s3.S3PresignedUrlGenerator;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Optional;
import java.util.UUID;

public class Server extends VerticleBase {
    private static final Logger log = LoggerFactory.getLogger(Server.class);

    private final FilesDAO filesDAO;
    private final S3PresignedUrlGenerator s3PresignedUrlGenerator;
    private final JsonObject config;

    public Server(FilesDAO filesDAO, S3PresignedUrlGenerator s3PresignedUrlGenerator, JsonObject config) {
        this.filesDAO = filesDAO;
        this.s3PresignedUrlGenerator = s3PresignedUrlGenerator;
        this.config = config;
    }

    @Override
    public Future<HttpServer> start() {
        Router router = Router.router(vertx);
        router.route().handler(this::traceIdMiddleware);
        router.route().handler(BodyHandler.create());

        String bucketName = config.getString("s3.bucket");

        router.get("/files/:fileId")
                .handler(new FileDownloadHandler(filesDAO, s3PresignedUrlGenerator));
        router.post("/files/init")
                .handler(new FileUploadInitHandler(filesDAO, s3PresignedUrlGenerator, bucketName));
        router.post("/files/complete/:fileId")
                .handler(new FileUploadCompleteHandler(filesDAO));

        int port = config.getInteger("server.port");
        return vertx.createHttpServer()
                .requestHandler(router)
                .listen(port)
                .onSuccess(s -> log.info("Server started on {} port", port))
                .onFailure(e -> log.error("Failed to deploy Server verticle on port {}", port, e));
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
