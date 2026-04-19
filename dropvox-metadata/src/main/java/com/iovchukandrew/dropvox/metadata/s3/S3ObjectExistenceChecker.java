package com.iovchukandrew.dropvox.metadata.s3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

public class S3ObjectExistenceChecker {
    private static final Logger log = LoggerFactory.getLogger(S3ObjectExistenceChecker.class);

    private final S3Client s3Client;

    public S3ObjectExistenceChecker(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public boolean objectExists(String bucket, String s3Key) {
        try {
            return s3Client.headObject(HeadObjectRequest.builder()
                            .bucket(bucket)
                            .key(s3Key)
                            .build())
                    .hasMetadata();
        } catch (NoSuchKeyException e) {
            return false;
        } catch (RuntimeException e) {
            log.error("Failed to check object existence in S3 for {bucket={}, key={}}", bucket, s3Key, e);
            throw e;
        }
    }
}
