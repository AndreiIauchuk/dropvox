package com.iovchukandrew.dropvox.metadata.server;

import com.iovchukandrew.dropvox.metadata.db.FilesDAO;
import com.iovchukandrew.dropvox.metadata.s3.S3PresignedUrlGenerator;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.UUID;

public class FileUploadHandler extends FileHandler {

    public FileUploadHandler(FilesDAO filesDAO, S3PresignedUrlGenerator s3PresignedUrlGenerator) {
        super(filesDAO, s3PresignedUrlGenerator);
    }

    //TODO FIX!!!
    @Override
    protected Future<JsonObject> handleRequest(UUID fileUuid, UUID userUuid) {
        //Save to DB
        var url = s3PresignedUrlGenerator.generatePutUrl(
                "dropvox-files", "minioadmin"
        );
        return Future.succeededFuture(JsonObject.of("uploadUrl", url));
    }
}
