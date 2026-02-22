package com.iovchukandrew.dropvox.metadata.s3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

/**
 * Generate presigned URLs.
 */
public class S3PresignedUrlGenerator {
    private static final Logger log = LoggerFactory.getLogger(S3PresignedUrlGenerator.class);

    private static final int DEFAULT_DURATION_MINS = 5;

    private final S3Presigner s3Presigner;

    public S3PresignedUrlGenerator(S3Presigner s3Presigner) {
        this.s3Presigner = s3Presigner;
    }

    public String generateGetUrl(String bucket, String s3Key, Duration expiration) {
        log.info("Generating presigned GET URL for {bucket={}, expiration={}}", bucket, expiration);

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .getObjectRequest(getObjectRequest)
                .build();

        var url = s3Presigner.presignGetObject(presignRequest).url().toString();
        log.info("Presigned GET URL was successfully generated for {bucket={}, expiration={}}", bucket, expiration);
        return url;
    }

    public String generateGetUrl(String bucket, String s3Key) {
        return generateGetUrl(bucket, s3Key, Duration.ofMinutes(DEFAULT_DURATION_MINS));
    }

    public String generatePutUrl(String bucket, String s3Key, Duration expiration) {
        log.info("Generating presigned PUT URL for {bucket={}, expiration={}}", bucket, expiration);
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(DEFAULT_DURATION_MINS))
                .putObjectRequest(putObjectRequest)
                .build();

        var url = s3Presigner.presignPutObject(presignRequest).url().toString();
        log.info("Presigned PUT URL was successfully generated for {bucket={}, expiration={}}", bucket, expiration);
        return url;
    }

    public String generatePutUrl(String bucket, String s3Key) {
        return generatePutUrl(bucket, s3Key, Duration.ofMinutes(DEFAULT_DURATION_MINS));
    }
}
