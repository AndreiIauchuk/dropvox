package com.iovchukandrew.dropvox.metadata.server;

import io.vertx.ext.web.RoutingContext;
import software.amazon.awssdk.http.HttpStatusCode;

import java.util.UUID;

public class UuidParser {

    public static UUID parsePathParam(RoutingContext ctx, String paramName) {
        String value = ctx.pathParam(paramName);
        return parse(ctx, value, paramName);
    }

    public static UUID parseHeader(RoutingContext ctx, String headerName) {
        String value = ctx.request().getHeader(headerName);
        return parse(ctx, value, headerName);
    }

    private static UUID parse(RoutingContext ctx, String value, String name) {
        if (value == null || value.isBlank()) {
            ctx.response().setStatusCode(HttpStatusCode.BAD_REQUEST).end("Missing " + name);
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            ctx.response().setStatusCode(HttpStatusCode.BAD_REQUEST).end("Invalid " + name);
            return null;
        }
    }
}
