package com.iovchukandrew.dropvox.metadata.s3;

import io.vertx.core.json.JsonObject;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

public final class S3ClientFactory {

    private S3ClientFactory() {
    }

    public static S3Client create(JsonObject config) {
        boolean pathStyleEnabled = config.getBoolean("s3.pathStyle", true);

        return S3Client.builder()
                .endpointOverride(URI.create(config.getString("s3.endpoint")))
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
