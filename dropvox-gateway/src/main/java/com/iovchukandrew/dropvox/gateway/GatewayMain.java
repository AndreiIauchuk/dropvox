package com.iovchukandrew.dropvox.gateway;

import com.iovchukandrew.dropvox.gateway.server.Server;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.micrometer.Label;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.micrometer.backends.BackendRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.vertx.micrometer.MicrometerMetricsOptions.DEFAULT_LABELS;

public class GatewayMain {
    private static final Logger log = LoggerFactory.getLogger(GatewayMain.class);

    public static void main(String[] args) {
        configureLogging();
        log.info("Application started");

        Vertx vertx = createVertx();
        WebClient webClient = WebClient.create(vertx);

        var configRetriever = ConfigRetrieverFactory.create(vertx);

        configRetriever.getConfig()
                .compose(GatewayMain::prepareConfig)
                .compose(config -> deployServer(vertx, webClient, config))
                .onSuccess(id -> {
                    log.info("Verticle deployed [id:{}]", id);
                    setupShutdownHook(vertx, webClient);
                })
                .onFailure(e -> {
                    log.error("Failed to start an application", e);
                    closeResources(vertx, webClient);
                });
    }

    private static Future<JsonObject> prepareConfig(JsonObject config) {
        Map<String, Object> updates = new HashMap<>();
        config.fieldNames().forEach(envKey -> {
            String configKey = envKey.toLowerCase().replace("_", ".");
            updates.put(configKey, config.getValue(envKey));
        });

        updates.forEach(config::put);
        return Future.succeededFuture(config);
    }

    private static Future<String> deployServer(Vertx vertx, WebClient webClient, JsonObject config) {
        return vertx.deployVerticle(new Server(webClient, config));
    }

    private static Vertx createVertx() {
        Vertx vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(
                new MicrometerMetricsOptions()
                        .setPrometheusOptions(new VertxPrometheusOptions()
                                .setEnabled(true)
                                .setPublishQuantiles(true))
                        .setLabels(createLabels())
                        .setEnabled(true)));
        bindJvmMetrics();
        return vertx;
    }

    private static EnumSet<Label> createLabels() {
        var labels = EnumSet.copyOf(DEFAULT_LABELS);
        labels.add(Label.HTTP_PATH);
        return labels;
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

    private static void setupShutdownHook(Vertx vertx, WebClient webClient) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> closeResources(vertx, webClient)));
    }

    private static void closeResources(Vertx vertx, WebClient webClient) {
        try {
            webClient.close();
            vertx.close().await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Unable to close resources during shutdown", e);
        }
    }

    private static void configureLogging() {
        System.setProperty(
                "vertx.logger-delegate-factory-class-name",
                "io.vertx.core.logging.SLF4JLogDelegateFactory");
    }
}
