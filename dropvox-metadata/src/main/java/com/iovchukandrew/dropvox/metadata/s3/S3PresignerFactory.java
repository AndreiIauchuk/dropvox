package com.iovchukandrew.dropvox.metadata.s3;

import io.vertx.core.json.JsonObject;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

public final class S3PresignerFactory {

    private S3PresignerFactory() {
    }

    public static S3Presigner create(JsonObject config) {
        String presignEndpoint = config.getString("s3.publicEndpoint", config.getString("s3.endpoint"));
        boolean pathStyleEnabled = config.getBoolean("s3.pathStyle", true);

        return S3Presigner.builder()
                .endpointOverride(URI.create(presignEndpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                config.getString("s3.accessKey"), config.getString("s3.secretKey"))))
                .region(Region.of(config.getString("s3.region", "us-east-1")))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyleEnabled)
                        .build())
                .build();
    }
}
