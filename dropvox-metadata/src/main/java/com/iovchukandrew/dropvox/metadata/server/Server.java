package com.iovchukandrew.dropvox.metadata.server;

import com.iovchukandrew.dropvox.metadata.db.MetadataDAO;
import com.iovchukandrew.dropvox.metadata.s3.S3PresignedUrlGenerator;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Server extends VerticleBase {
    private static final Logger log = LoggerFactory.getLogger(Server.class);

    private final MetadataDAO metadataDAO;
    private final S3PresignedUrlGenerator s3PresignedUrlGenerator;

    public Server(MetadataDAO metadataDAO, S3PresignedUrlGenerator s3PresignedUrlGenerator) {
        this.metadataDAO = metadataDAO;
        this.s3PresignedUrlGenerator = s3PresignedUrlGenerator;
    }

    @Override
    public Future<HttpServer> start() {
        Router router = Router.router(vertx);

        FileDownloadHandler handler = new FileDownloadHandler(metadataDAO, s3PresignedUrlGenerator);
        router.get("/files/:id").handler(handler::handle);

       return vertx.createHttpServer()
                .requestHandler(router)
                .listen(8082)
               .onSuccess(s -> log.info("Server started on port 8082"))
               .onFailure(Throwable::printStackTrace); //TODO Should stop the whole app!
        /*
         vertx.deployVerticle(new Server(metadataDAO, urlGenerator, port))
        .onSuccess(id -> {
            log.info("Verticle deployed, id: {}", id);
            // Можно сохранить ссылку на сервер, если нужно
        })
        .onFailure(err -> {
            log.error("Failed to deploy verticle", err);

            // 1. Закрываем ресурсы (порядок важен: сначала зависимые, потом Vertx)
            closeSqlPool(sqlPool);
            closeS3Presigner(s3Presigner);
            vertx.close()
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        log.info("Vertx closed after deployment failure");
                    } else {
                        log.error("Error closing Vertx", ar.cause());
                    }
                    // 2. Освобождаем latch, чтобы main завершился
                    shutdownLatch.countDown();
                    // 3. Завершаем процесс с ненулевым кодом (опционально)
                    System.exit(1);
                });
        });*/
    }
}
