package com.iovchukandrew.dropvox.auth;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.PrometheusScrapingHandler;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.micrometer.backends.BackendRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Optional;
import java.util.UUID;

public class AuthMain {
    private static final Logger log = LoggerFactory.getLogger(AuthMain.class);
    private static final String TRACE_ID = "X-Trace-Id";
    private static final String USER_ID = "3b069db6-b46a-4766-96d8-9bee5c5a32be";

    public static void main(String[] args) {
        configureLogging();
        Vertx vertx = createVertx();

        Router router = Router.router(vertx);
        router.route().handler(AuthMain::traceIdMiddleware);
        router.route().handler(BodyHandler.create());
        router.get("/health/live").handler(ctx -> respondWithStatus(ctx, "live"));
        router.get("/health/ready").handler(ctx -> respondWithStatus(ctx, "ready"));
        router.get("/metrics").handler(PrometheusScrapingHandler.create());
        router.post("/validate").handler(ctx -> ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("userId", USER_ID).encode()));

        int port = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8081"));
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port)
                .onSuccess(server -> log.info("Auth service started on port {}", server.actualPort()))
                .onFailure(error -> {
                    log.error("Failed to start auth service", error);
                    vertx.close();
                });
    }

    private static Vertx createVertx() {
        Vertx vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(
                new MicrometerMetricsOptions()
                        .setPrometheusOptions(new VertxPrometheusOptions()
                                .setEnabled(true)
                                .setPublishQuantiles(true))
                        .setEnabled(true)));
        bindJvmMetrics();
        return vertx;
    }

    private static void bindJvmMetrics() {
        MeterRegistry registry = BackendRegistries.getDefaultNow();
        if (registry == null) {
            return;
        }

        try (JvmGcMetrics jvmGcMetrics = new JvmGcMetrics()) {
            jvmGcMetrics.bindTo(registry);
        }
        new ClassLoaderMetrics().bindTo(registry);
        new JvmMemoryMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new ProcessorMetrics().bindTo(registry);
    }

    private static void traceIdMiddleware(io.vertx.ext.web.RoutingContext ctx) {
        String traceId = Optional.ofNullable(ctx.request().getHeader(TRACE_ID))
                .orElse(UUID.randomUUID().toString());

        MDC.put("traceId", traceId);
        ctx.response().putHeader(TRACE_ID, traceId);
        ctx.addEndHandler(v -> MDC.remove("traceId"));
        ctx.next();
    }

    private static void respondWithStatus(io.vertx.ext.web.RoutingContext ctx, String check) {
        ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                        .put("status", "UP")
                        .put("check", check)
                        .encode());
    }

    private static void configureLogging() {
        System.setProperty(
                "vertx.logger-delegate-factory-class-name",
                "io.vertx.core.logging.SLF4JLogDelegateFactory");
    }
}
