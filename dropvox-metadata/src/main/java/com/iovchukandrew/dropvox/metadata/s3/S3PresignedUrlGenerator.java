package com.iovchukandrew.dropvox.metadata.s3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

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
        log.info("Generating PresignedUrl");
        return "mocked PresignedUrl by bucket [ " + bucket + " ] and key [" + s3Key + "]";
        /*GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();*/
    }

    public String generateGetUrl(String bucket, String s3Key) {
        return generateGetUrl(bucket, s3Key, Duration.ofMinutes(DEFAULT_DURATION_MINS));
        /*GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();*/
    }

    public String generatePutUrl(String s3Key) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}