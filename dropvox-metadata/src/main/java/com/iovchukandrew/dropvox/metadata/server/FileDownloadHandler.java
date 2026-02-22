package com.iovchukandrew.dropvox.metadata.server;

import com.iovchukandrew.dropvox.metadata.db.FilesDAO;
import com.iovchukandrew.dropvox.metadata.s3.S3PresignedUrlGenerator;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.UUID;

/**
 * Handles GET /files/:id requests.
 */
public class FileDownloadHandler extends FileHandler {

    public FileDownloadHandler(
            FilesDAO filesDAO,
            S3PresignedUrlGenerator s3PresignedUrlGenerator
    ) {
        super(filesDAO, s3PresignedUrlGenerator);
    }

    @Override
    protected Future<JsonObject> handleRequest(UUID fileUuid, UUID userUuid) {
        return filesDAO.findFileByIdAndOwner(fileUuid, userUuid)
                .map(metadata -> {
                    String presignedUrl = s3PresignedUrlGenerator.generateGetUrl(
                            metadata.getString("bucket"),
                            metadata.getString("s3Key")
                    );
                    metadata.put("downloadUrl", presignedUrl);
                    return metadata;
                });
    }
}
