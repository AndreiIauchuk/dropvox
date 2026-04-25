package com.iovchukandrew.dropvox.gateway.client;

public class MetadataServiceException extends RuntimeException {
    private final int statusCode;

    public MetadataServiceException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
