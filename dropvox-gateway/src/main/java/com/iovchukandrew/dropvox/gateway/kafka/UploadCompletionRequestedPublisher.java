package com.iovchukandrew.dropvox.gateway.kafka;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Publishes asynchronous file upload completion requests to Kafka.
 */
public class UploadCompletionRequestedPublisher {
    private static final Logger log = LoggerFactory.getLogger(UploadCompletionRequestedPublisher.class);

    private final KafkaProducer<String, String> producer;
    private final String topic;

    private UploadCompletionRequestedPublisher(KafkaProducer<String, String> producer, String topic) {
        this.producer = producer;
        this.topic = topic;
    }

    public static UploadCompletionRequestedPublisher create(Vertx vertx, JsonObject config) {
        Map<String, String> producerConfig = new HashMap<>();
        producerConfig.put("bootstrap.servers", config.getString("kafka.bootstrap.servers", "localhost:9092"));
        producerConfig.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerConfig.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerConfig.put("acks", config.getString("kafka.producer.acks", "all"));
        producerConfig.put("enable.idempotence",
                String.valueOf(config.getBoolean("kafka.producer.idempotence", true)));

        String topic = config.getString(
                "kafka.upload.completion.requested.topic",
                "file-upload-completion-requested"
        );

        KafkaProducer<String, String> producer = KafkaProducer.create(vertx, producerConfig);
        log.info("Kafka upload completion publisher configured for topic={} bootstrapServers={}",
                topic,
                producerConfig.get("bootstrap.servers"));
        return new UploadCompletionRequestedPublisher(producer, topic);
    }

    public Future<Void> publish(String fileId, String userId, String traceId) {
        JsonObject event = new JsonObject()
                .put("eventType", "FileUploadCompletionRequested")
                .put("fileId", fileId)
                .put("userId", userId)
                .put("traceId", traceId)
                .put("requestedAt", Instant.now().toString());

        KafkaProducerRecord<String, String> record = KafkaProducerRecord.create(topic, fileId, event.encode());
        return producer.send(record).mapEmpty();
    }

    public Future<Void> close() {
        return producer.close();
    }
}
