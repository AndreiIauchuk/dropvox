package com.iovchukandrew.dropvox.metadata.server;

public class FileNotYetUploadedException extends RuntimeException {

    public static final String FILE_NOT_YET_UPLOADED_ERROR_MSG = "File has not been uploaded yet";

    public FileNotYetUploadedException() {
        super(FILE_NOT_YET_UPLOADED_ERROR_MSG);
    }

    public FileNotYetUploadedException(String message) {
        super(message);
    }
}
