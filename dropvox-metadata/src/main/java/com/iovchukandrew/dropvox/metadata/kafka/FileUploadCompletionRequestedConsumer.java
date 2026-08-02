package com.iovchukandrew.dropvox.metadata.kafka;

import com.iovchukandrew.dropvox.metadata.processing.UploadCompletionProcessor;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Consumes upload-completion requests produced by gateway and completes uploads asynchronously.
 */
public class FileUploadCompletionRequestedConsumer {
    private static final Logger log = LoggerFactory.getLogger(FileUploadCompletionRequestedConsumer.class);

    private final KafkaConsumer<String, String> consumer;
    private final UploadCompletionProcessor uploadCompletionProcessor;
    private final String topic;

    private FileUploadCompletionRequestedConsumer(
            KafkaConsumer<String, String> consumer,
            UploadCompletionProcessor uploadCompletionProcessor,
            String topic
    ) {
        this.consumer = consumer;
        this.uploadCompletionProcessor = uploadCompletionProcessor;
        this.topic = topic;
    }

    public static FileUploadCompletionRequestedConsumer create(
            Vertx vertx,
            JsonObject config,
            UploadCompletionProcessor uploadCompletionProcessor
    ) {
        Map<String, String> consumerConfig = new HashMap<>();
        consumerConfig.put("bootstrap.servers", config.getString("kafka.bootstrap.servers", "localhost:9092"));
        consumerConfig.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerConfig.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerConfig.put("group.id",
                config.getString("kafka.upload.completion.requested.group.id", "metadata-upload-completion-workers"));
        consumerConfig.put("auto.offset.reset", config.getString("kafka.consumer.auto.offset.reset", "latest"));
        consumerConfig.put("enable.auto.commit", "true");

        String topic = config.getString(
                "kafka.upload.completion.requested.topic",
                "file-upload-completion-requested"
        );

        return new FileUploadCompletionRequestedConsumer(
                KafkaConsumer.create(vertx, consumerConfig),
                uploadCompletionProcessor,
                topic
        );
    }

    public Future<Void> start() {
        return consumer
                .exceptionHandler(err -> log.error("Kafka completion-request consumer error", err))
                .handler(this::handleRecord)
                .subscribe(topic)
                .onSuccess(ignored -> log.info("Subscribed to Kafka topic {} for upload completion requests", topic));
    }

    public Future<Void> close() {
        return consumer.close();
    }

    private void handleRecord(KafkaConsumerRecord<String, String> record) {
        JsonObject event;
        try {
            event = new JsonObject(record.value());
        } catch (Exception e) {
            log.error("Skipping malformed completion request event: {}", record.value(), e);
            return;
        }

        String fileId = event.getString("fileId");
        String userId = event.getString("userId");
        String traceId = event.getString("traceId");

        if (fileId == null || userId == null) {
            log.warn("Skipping completion request event with missing identifiers: {}", event.encode());
            return;
        }

        final UUID fileUuid;
        final UUID userUuid;
        try {
            fileUuid = UUID.fromString(fileId);
            userUuid = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            log.warn("Skipping completion request event with invalid UUIDs fileId={} userId={}", fileId, userId);
            return;
        }

        uploadCompletionProcessor.processRequestedCompletion(fileUuid, userUuid)
                .onSuccess(ignored -> log.info(
                        "Upload completion processing finished via Kafka event for fileId={}, userId={}, traceId={}",
                        fileId, userId, traceId))
                .onFailure(err -> log.error("Failed to process upload completion Kafka event for fileId={}, userId={}, traceId={}",
                        fileId, userId, traceId, err));
    }
}

