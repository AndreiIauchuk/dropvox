package org.iovchukandrew.dropvox.gateway.server;

import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
class FileDownloadHandlerTest {

    // @Mock
    // private AuthServiceClient authServiceClient;

    // @Mock
    // private MetadataServiceClient metadataServiceClient;

    // @Mock
    // private RoutingContext routingContext;

    // @Mock
    // private io.vertx.ext.web.HttpServerResponse response;

    // private FileDownloadHandler handler;

    // @BeforeEach
    // void setUp() {
    // handler = new FileDownloadHandler(null, authServiceClient,
    // metadataServiceClient);
    // when(routingContext.response()).thenReturn(response);
    // when(response.setStatusCode(anyInt())).thenReturn(response);
    // when(response.putHeader(anyString(), anyString())).thenReturn(response);
    // }

    // @Disabled("TO BE DELETED")
    // @Test
    // void basicRouteTest(VertxTestContext testContext) {
    // Checkpoint requestsServed = testContext.checkpoint(10);

    // for (int i = 0; i < 10; i++) {
    // webClient.get(8888, "localhost", "/")
    // .send()
    // .onComplete(testContext.succeeding(response -> {
    // testContext.verify(() -> {
    // JsonObject json = response.bodyAsJsonObject();
    // assertThat(json.getString("name")).isEqualTo("unknown");
    // assertThat(json.getString("address")).contains("127.0.0.1");
    // assertThat(json.getString("message")).contains("Hello unknown connected from
    // 127.0.0.1");
    // requestsServed.flag();
    // });
    // }));
    // }
    // }

    // @Test
    // void testHandleSuccess() {
    // String fileId = "file123";
    // String userId = "user456";
    // JsonObject metadata = new JsonObject().put("name",
    // "document.pdf").put("size", 1024);

    // when(routingContext.pathParam("id")).thenReturn(fileId);
    // when(authServiceClient.validateToken("token")).thenReturn(Future.succeededFuture(userId));
    // when(metadataServiceClient.fetchFileMetadata(fileId,
    // userId)).thenReturn(Future.succeededFuture(metadata));

    // handler.handle(routingContext);

    // verify(response).setStatusCode(200);
    // verify(response).putHeader("Content-Type", "application/json");
    // verify(response).end(metadata.toBuffer());
    // }

    // @Test
    // void testHandleAuthServiceFailure() {
    // String fileId = "file123";
    // Throwable error = new RuntimeException("Auth failed");

    // when(routingContext.pathParam("id")).thenReturn(fileId);
    // when(authServiceClient.validateToken("token")).thenReturn(Future.failedFuture(error));

    // handler.handle(routingContext);

    // verify(response).setStatusCode(500);
    // verify(response).end("Auth failed");
    // }

    // @Test
    // void testHandleMetadataServiceFailure() {
    // String fileId = "file123";
    // String userId = "user456";
    // Throwable error = new RuntimeException("Metadata not found");

    // when(routingContext.pathParam("id")).thenReturn(fileId);
    // when(authServiceClient.validateToken("token")).thenReturn(Future.succeededFuture(userId));
    // when(metadataServiceClient.fetchFileMetadata(fileId,
    // userId)).thenReturn(Future.failedFuture(error));

    // handler.handle(routingContext);

    // verify(response).setStatusCode(500);
    // verify(response).end("Metadata not found");
    // }
}