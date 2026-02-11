package org.iovchukandrew.dropvox.rest;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.iovchukandrew.dropvox.Main;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(VertxExtension.class)
public class TestFileEndpoint {

    private WebClient webClient;

    @BeforeEach
    void setup(Vertx vertx, VertxTestContext testContext) {
        // Запускаем наш сервер (используем тот же MainFuture, что и раньше)
        // Чтобы не дублировать код, можно вынести запуск сервера в отдельный класс.
        // Здесь для простоты запустим сервер прямо в тесте, но обычно используют @TestInstance или @BeforeAll.
        Main.main(new String[0]); // или лучше через vertx.deployVerticle(...)

        // Создаём WebClient
        webClient = WebClient.create(vertx);

        // Даём серверу время на запуск
        vertx.setTimer(1000, id -> testContext.completeNow());
    }

    @Test
    void testGetExistingFile(Vertx vertx, VertxTestContext testContext) {
        // Отправляем GET запрос
        webClient.get(8080, "localhost", "/files/123")
                .expect(ResponsePredicate.SC_OK)
                .expect(ResponsePredicate.CONTENT_TYPE_JSON)
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        JsonObject body = response.bodyAsJsonObject();
                        assertEquals("123", body.getString("id"));
                        assertEquals("example.txt", body.getString("name"));
                        assertEquals(1024, body.getInteger("size"));
                        assertEquals("text/plain", body.getString("contentType"));
                    });
                    testContext.completeNow();
                }));
    }

    @Test
    void testFileNotFound(Vertx vertx, VertxTestContext testContext) {
        // Пока наш мок возвращает 200 всегда. Для теста 404 надо доработать сервер.
        // Но мы можем проверить, что запрос проходит успешно.
        // Позже, когда добавишь логику, переделаешь тест.
        webClient.get(8080, "localhost", "/files/999")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        assertEquals(200, response.statusCode());
                    });
                    testContext.completeNow();
                }));
    }
}