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

    private final S3Presigner s3Presigner;
    private final String bucketName;
    private final Duration expiration;

    public S3PresignedUrlGenerator(S3Presigner s3Presigner, String bucketName, Duration expiration) {
        this.s3Presigner = s3Presigner;
        this.bucketName = bucketName;
        this.expiration = expiration;
    }

    public String generateGetUrl(String s3Key) {
        log.info("Generating PresignedUrl");
        return "mocked PresignedUrl by " + s3Key;
        /*GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
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