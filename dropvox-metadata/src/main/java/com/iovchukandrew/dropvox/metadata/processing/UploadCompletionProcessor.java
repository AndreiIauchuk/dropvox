package com.iovchukandrew.dropvox.metadata.processing;

import com.iovchukandrew.dropvox.metadata.db.FileMetadataNotFoundException;
import com.iovchukandrew.dropvox.metadata.db.FileStatus;
import com.iovchukandrew.dropvox.metadata.db.FilesDAO;
import com.iovchukandrew.dropvox.metadata.s3.S3ObjectExistenceChecker;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static com.iovchukandrew.dropvox.metadata.db.FileStatus.*;

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

    /**
     * Handles MinIO "object created" notifications by trying to confirm a matching pending upload in DB.
     *
     * <p>The event stream is treated as best-effort: if no pending row matches the object location,
     * the method completes successfully and only logs at debug level.
     *
     * @param bucket S3 bucket from the MinIO event
     * @param s3Key object key from the MinIO event
     * @return a future that completes when processing finishes
     */
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

    /**
     * Processes an explicit upload completion request for a file owned by a user.
     *
     * <p>This method is intentionally idempotent and can be called more than once for the same file.
     * Duplicate invocations happen in normal operation, for example when clients retry
     * {@code POST /files/:fileId/completion-request} after network timeouts or when at-least-once
     * delivery causes repeated completion signaling. Repeated calls must not create duplicate transitions.
     *
     * <p>Behavior by state:
     * <ul>
     *   <li>{@code PENDING}: checks object existence (with retries) and transitions to {@code UPLOADED}
     *   or {@code FAILED}</li>
     *   <li>{@code UPLOADED}/{@code FAILED}: no-op success</li>
     *   <li>any unknown status: fails the future</li>
     * </ul>
     *
     * @param fileUuid file identifier
     * @param userUuid owner identifier
     * @return a future that completes when the request has been applied (or safely ignored)
     */
    public Future<Void> processRequestedCompletion(UUID fileUuid, UUID userUuid) {
        return filesDAO.findFileByIdAndOwnerAnyStatus(fileUuid, userUuid)
                .compose(metadata -> processCompletionForMetadata(metadata, fileUuid, userUuid));
    }

    private Future<Void> processCompletionForMetadata(JsonObject metadata, UUID fileUuid, UUID userUuid) {
        FileStatus status = FileStatus.valueOf(metadata.getString("status"));
        if (UPLOADED.equals(status)) {
            log.info("File already uploaded, skipping duplicate completion request for fileId={}, ownerId={}", fileUuid, userUuid);
            return Future.succeededFuture();
        }
        if (FAILED.equals(status)) {
            log.warn("File already marked as failed, skipping completion request for fileId={}, ownerId={}", fileUuid, userUuid);
            return Future.succeededFuture();
        }
        if (!PENDING.equals(status)) {
            return Future.failedFuture("Unsupported file status: " + status);
        }

        return updatePendingFileInDbAfterExistenceCheck(metadata, fileUuid, userUuid)
                .recover(err -> recoverFailedUpdatingInDb(err, fileUuid, userUuid));
    }

    private Future<Void> updatePendingFileInDbAfterExistenceCheck(JsonObject metadata, UUID fileUuid, UUID userUuid) {
        String bucket = metadata.getString("bucket");
        String s3Key = metadata.getString("s3Key");

        return checkObjectExistsWithRetry(bucket, s3Key, 1)
                .compose(objectExists -> {
                    if (!objectExists) {
                        return filesDAO.markPendingFileUploadAsFailed(fileUuid, userUuid).mapEmpty();
                    }
                    return filesDAO.confirmFileUpload(fileUuid, userUuid).mapEmpty();
                });
    }

    private Future<Void> recoverFailedUpdatingInDb(Throwable err, UUID fileUuid, UUID userUuid) {
        if (err instanceof FileMetadataNotFoundException) {
            log.info("File already processed, skipping fileId={}, ownerId={}", fileUuid, userUuid);
            return Future.succeededFuture();
        }
        return Future.failedFuture(err);
    }

    private Future<Boolean> checkObjectExistsWithRetry(String bucket, String s3Key, int attempt) {
        return vertx.executeBlocking(() ->
                s3ObjectExistenceChecker.objectExists(bucket, s3Key)
        ).compose(objectExists -> {
            if (objectExists || attempt >= maxS3ExistenceCheckAttempts) {
                return Future.succeededFuture(objectExists);
            }

            return Future.<Void>future(promise ->
                    vertx.setTimer(s3ExistenceRetryDelayMs, ignored -> promise.complete())
            ).compose(ignored -> checkObjectExistsWithRetry(bucket, s3Key, attempt + 1));
        });
    }
}
