package com.iovchukandrew.dropvox.metadata.server;

import com.iovchukandrew.dropvox.metadata.db.FilesDAO;
import com.iovchukandrew.dropvox.metadata.s3.S3PresignedUrlGenerator;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        router.get("/files/:id")
                .handler(new FileDownloadHandler(filesDAO, s3PresignedUrlGenerator));
        router.post("/files/:id")
                .handler(new FileUploadHandler(filesDAO, s3PresignedUrlGenerator));

        int port = config.getInteger("server.port");
        return vertx.createHttpServer()
                .requestHandler(router)
                .listen(port)
                .onSuccess(s -> log.info("Server started on {} port", port))
                .onFailure(e -> log.error("Failed to deploy Server verticle on port {}", port, e));
    }
}
