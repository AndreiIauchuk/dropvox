package org.iovchukandrew.dropvox.metadata;

import io.vertx.core.Vertx;

import org.iovchukandrew.dropvox.metadata.db.MetadataDAO;
import org.iovchukandrew.dropvox.metadata.s3.S3PresignedUrlGenerator;
import org.iovchukandrew.dropvox.metadata.server.Server;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;

public class MetadataMain {
    public static void main(String[] args) {
        try (S3Presigner s3Presigner = createS3Presigner()) {
            Vertx vertx = Vertx.vertx();
            var metadataDAO = new MetadataDAO(vertx);
            var s3PresignedUrlGenerator = createS3PresignedUrlGenerator(s3Presigner);
            startServer(vertx, metadataDAO, s3PresignedUrlGenerator);
        }
    }

    private static void startServer(
            Vertx vertx,
            MetadataDAO metadataDAO,
            S3PresignedUrlGenerator s3PresignedUrlGenerator
    ) {
        vertx.deployVerticle(new Server(metadataDAO, s3PresignedUrlGenerator))
                .onSuccess(id -> System.out.println("Verticle deployed, id: " + id))
                .onFailure(Throwable::printStackTrace);
    }

    private static S3PresignedUrlGenerator createS3PresignedUrlGenerator(S3Presigner s3Presigner) {
        return new S3PresignedUrlGenerator(s3Presigner, "bucketName", Duration.ofMinutes(5));
    }

    private static S3Presigner createS3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create("http://minio:9000"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("minioadmin", "minioadmin")))
                .region(Region.US_EAST_1)
                .build();
    }
}