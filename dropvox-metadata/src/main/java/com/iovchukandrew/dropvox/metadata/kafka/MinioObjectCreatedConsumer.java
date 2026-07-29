package com.iovchukandrew.dropvox.metadata.kafka;

import com.iovchukandrew.dropvox.metadata.processing.UploadCompletionProcessor;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Consumes MinIO object-created notifications from Kafka and auto-confirms pending uploads.
 */
public class MinioObjectCreatedConsumer {
    private static final Logger log = LoggerFactory.getLogger(MinioObjectCreatedConsumer.class);

    private final KafkaConsumer<String, String> consumer;
    private final UploadCompletionProcessor uploadCompletionProcessor;
    private final String topic;

    private MinioObjectCreatedConsumer(
            KafkaConsumer<String, String> consumer,
            UploadCompletionProcessor uploadCompletionProcessor,
            String topic
    ) {
        this.consumer = consumer;
        this.uploadCompletionProcessor = uploadCompletionProcessor;
        this.topic = topic;
    }

    public static MinioObjectCreatedConsumer create(
            Vertx vertx,
            JsonObject config,
            UploadCompletionProcessor uploadCompletionProcessor
    ) {
        Map<String, String> consumerConfig = new HashMap<>();
        consumerConfig.put("bootstrap.servers", config.getString("kafka.bootstrap.servers", "localhost:9092"));
        consumerConfig.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerConfig.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerConfig.put("group.id", config.getString("kafka.minio.events.group.id", "metadata-minio-events"));
        consumerConfig.put("auto.offset.reset", config.getString("kafka.consumer.auto.offset.reset", "latest"));
        consumerConfig.put("enable.auto.commit", "true");

        String topic = config.getString("kafka.minio.events.topic", "minio-object-events");

        return new MinioObjectCreatedConsumer(
                KafkaConsumer.create(vertx, consumerConfig),
                uploadCompletionProcessor,
                topic
        );
    }

    public Future<Void> start() {
        return consumer.exceptionHandler(err -> log.error("Kafka MinIO-event consumer error", err))
                .handler(this::handleRecord)
                .subscribe(topic)
                .onSuccess(ignored -> log.info("Subscribed to Kafka topic {} for MinIO object events", topic));
    }

    public Future<Void> close() {
        return consumer.close();
    }

    private void handleRecord(KafkaConsumerRecord<String, String> record) {
        JsonObject payload;
        try {
            payload = new JsonObject(record.value());
        } catch (Exception e) {
            log.error("Skipping malformed MinIO event payload: {}", record.value(), e);
            return;
        }

        JsonArray records = payload.getJsonArray("Records");
        if (records == null || records.isEmpty()) {
            return;
        }

        for (int i = 0; i < records.size(); i++) {
            JsonObject eventRecord = records.getJsonObject(i);
            if (eventRecord == null) {
                continue;
            }

            String eventName = eventRecord.getString("eventName", eventRecord.getString("EventName", ""));
            if (!eventName.startsWith("s3:ObjectCreated")) {
                continue;
            }

            JsonObject s3 = eventRecord.getJsonObject("s3");
            if (s3 == null) {
                continue;
            }

            JsonObject bucketJson = s3.getJsonObject("bucket");
            JsonObject objectJson = s3.getJsonObject("object");
            if (bucketJson == null || objectJson == null) {
                continue;
            }

            String bucket = bucketJson.getString("name");
            String encodedKey = objectJson.getString("key");
            if (bucket == null || encodedKey == null) {
                continue;
            }

            String s3Key = URLDecoder.decode(encodedKey, StandardCharsets.UTF_8);
            uploadCompletionProcessor.processMinioObjectCreated(bucket, s3Key)
                    .onSuccess(ignored -> log.info(
                            "MinIO confirmed upload for event for bucket={}, s3Key={}, eventName={}",
                            bucket,
                            s3Key,
                            eventName
                    ))
                    .onFailure(err -> log.error(
                            "Failed processing MinIO object event for bucket={}, s3Key={}, eventName={}",
                            bucket,
                            s3Key,
                            eventName,
                            err
                    ));
        }
    }
}

