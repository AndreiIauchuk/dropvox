package org.iovchukandrew.dropvox;

import io.vertx.core.Vertx;
import org.iovchukandrew.dropvox.rest.RestVerticle;

public class Main {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new RestVerticle())
                .onSuccess(id -> System.out.println("Verticle deployed, id: " + id))
                .onFailure(Throwable::printStackTrace);
    }
}