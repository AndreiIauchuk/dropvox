package org.iovchukandrew.dropvox.metadata.server;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import org.iovchukandrew.dropvox.metadata.db.MetadataDAO;
import org.iovchukandrew.dropvox.metadata.s3.S3PresignedUrlGenerator;

public class Server extends VerticleBase {

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
                .onSuccess(s -> System.out.println("Metadata Service started on port 8082"))
                .onFailure(Throwable::printStackTrace);
    }
}
