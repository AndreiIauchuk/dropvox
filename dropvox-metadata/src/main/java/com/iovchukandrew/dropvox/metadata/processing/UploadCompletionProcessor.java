package com.iovchukandrew.dropvox.metadata.processing;

import com.iovchukandrew.dropvox.metadata.db.FileMetadataNotFoundException;
import com.iovchukandrew.dropvox.metadata.db.FilesDAO;
import com.iovchukandrew.dropvox.metadata.s3.S3ObjectExistenceChecker;
import com.iovchukandrew.dropvox.metadata.server.FileNotYetUploadedException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Asynchronously confirms uploads by verifying S3 object existence and transitioning DB state.
 */
public class UploadCompletionProcessor {
    private static final Logger log = LoggerFactory.getLogger(UploadCompletionProcessor.class);

    private final Vertx vertx;
    private final FilesDAO filesDAO;
    private final S3ObjectExistenceChecker s3ObjectExistenceChecker;
    private final int maxS3ExistenceCheckAttempts;
    private final long s3ExistenceRetryDelayMs;

    public UploadCompletionProcessor(
            Vertx vertx,
            FilesDAO filesDAO,
            S3ObjectExistenceChecker s3ObjectExistenceChecker,
            int maxS3ExistenceCheckAttempts,
            long s3ExistenceRetryDelayMs
    ) {
        this.vertx = vertx;
        this.filesDAO = filesDAO;
        this.s3ObjectExistenceChecker = s3ObjectExistenceChecker;
        this.maxS3ExistenceCheckAttempts = maxS3ExistenceCheckAttempts;
        this.s3ExistenceRetryDelayMs = s3ExistenceRetryDelayMs;
    }

    public Future<Void> processRequestedCompletion(UUID fileUuid, UUID userUuid) {
        return filesDAO.findPendingFileByIdAndOwner(fileUuid, userUuid)
                .compose(metadata -> checkObjectExistsWithRetry(metadata, 1))
                .compose(objectExists -> {
                    if (!objectExists) {
                        return Future.failedFuture(new FileNotYetUploadedException());
                    }
                    return filesDAO.confirmFileUpload(fileUuid, userUuid).mapEmpty();
                });
    }

    public Future<Void> processMinioObjectCreated(String bucket, String s3Key) {
        return filesDAO.confirmPendingFileUploadByObjectLocation(bucket, s3Key)
                .map(ignored -> (Void) null)
                .recover(err -> {
                    if (err instanceof FileMetadataNotFoundException) {
                        log.debug("No pending file matched MinIO object event bucket={}, s3Key={}", bucket, s3Key);
                        return Future.succeededFuture();
                    }
                    return Future.failedFuture(err);
                });
    }

    private Future<Boolean> checkObjectExistsWithRetry(JsonObject metadata, int attempt) {
        return vertx.executeBlocking(() ->
                s3ObjectExistenceChecker.objectExists(
                        metadata.getString("bucket"),
                        metadata.getString("s3Key")
                )
        ).compose(objectExists -> {
            if (objectExists || attempt >= maxS3ExistenceCheckAttempts) {
                return Future.succeededFuture(objectExists);
            }

            return Future.<Void>future(promise ->
                    vertx.setTimer(s3ExistenceRetryDelayMs, ignored -> promise.complete())
            ).compose(ignored -> checkObjectExistsWithRetry(metadata, attempt + 1));
        });
    }
}



