package org.iovchukandrew.dropvox.gateway.rest;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientAgent;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Deprecated
public class ATest {
    Vertx vertx = Vertx.vertx();

    @Test
    void shouldExecAllCheckpoints() {
        VertxTestContext testContext = new VertxTestContext();
        Checkpoint serverStarted = testContext.checkpoint();
        Checkpoint requestsServed = testContext.checkpoint(10);
        Checkpoint responsesReceived = testContext.checkpoint(10);

        vertx.createHttpServer()
                .requestHandler(req -> {
                    req.response().end();
                    requestsServed.flag();
                })
                .listen(16969)
                .onComplete(testContext.succeeding(
                        httpServer -> {
                            serverStarted.flag();
                            HttpClientAgent client = vertx.createHttpClient();
                            client.request(HttpMethod.GET, 16969, "localhost", "/")
                                    .compose(req -> req.send().compose(HttpClientResponse::body))
                                    .onComplete(testContext.succeeding(
                                            buffer -> testContext.verify(() -> {
                                                assertThat(buffer.toString()).isEqualTo("Ok");
                                                responsesReceived.flag();
                                            })));
                        }));
    }
}
