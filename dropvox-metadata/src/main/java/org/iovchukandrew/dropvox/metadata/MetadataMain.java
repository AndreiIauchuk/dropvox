package org.iovchukandrew.dropvox.metadata;

import io.vertx.core.Vertx;

import io.vertx.core.http.PoolOptions;
import org.iovchukandrew.dropvox.metadata.db.MetadataDAO;
import org.iovchukandrew.dropvox.metadata.server.Server;

import javax.swing.plaf.synth.Region;
import java.net.URI;

public class MetadataMain {
    public static void main(String[] args) {
        startServer();
    }

    private static void startServer() {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new Server())
                .onSuccess(id -> System.out.println("Verticle deployed, id: " + id))
                .onFailure(Throwable::printStackTrace);

        // PostgreSQL configuration
        PgConnectOptions pgConnectOptions = new PgConnectOptions()
                .setPort(5432)
                .setHost("postgres-service")
                .setDatabase("dropvox")
                .setUser("user")
                .setPassword("pass");

        PoolOptions poolOptions = new PoolOptions().setMaxSize(20);
        PgPool pgPool = PgPool.pool(vertx, pgConnectOptions, poolOptions);

        // S3 Presigner configuration (MinIO)
        S3Presigner s3Presigner = S3Presigner.builder()
                .endpointOverride(URI.create("http://minio:9000"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("minioadmin", "minioadmin")))
                .region(Region.US_EAST_1)  // can be any, but required
                .build();

        String bucketName = "dropvox-files";

        // Create DAO and handler
        MetadataDAO metadataDAO = new MetadataDAO(pgPool);
        MetadataFileHandler fileHandler = new MetadataFileHandler(metadataDAO, s3Presigner, bucketName);
    }
}