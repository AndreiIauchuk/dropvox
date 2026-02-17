package com.iovchukandrew.dropvox.metadata.server;

import com.iovchukandrew.dropvox.metadata.db.MetadataDAO;
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

    private final MetadataDAO metadataDAO;
    private final S3PresignedUrlGenerator s3PresignedUrlGenerator;
    private final JsonObject config;

    public Server(MetadataDAO metadataDAO, S3PresignedUrlGenerator s3PresignedUrlGenerator, JsonObject config) {
        this.metadataDAO = metadataDAO;
        this.s3PresignedUrlGenerator = s3PresignedUrlGenerator;
        this.config = config;
    }

    @Override
    public Future<HttpServer> start() {
        Router router = Router.router(vertx);

        FileDownloadHandler handler = new FileDownloadHandler(metadataDAO, s3PresignedUrlGenerator);
        router.get("/files/:id").handler(handler::handle);

        return vertx.createHttpServer()
                .requestHandler(router)
                .listen(config.getInteger("server.port"))
                .onSuccess(s -> log.info("Server started on port 8082"))
                .onFailure(e -> log.error("Failed to deploy Server verticle", e));
    }
}
