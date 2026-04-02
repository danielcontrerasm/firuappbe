// com.example.pettracker.kafka.GpsKafkaProducer
package com.example.pettracker.gps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GpsKafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${gps.kafka.topic:gps-locations}")
    private String topic;

    public void sendLocation(String imei, double lat, double lon, String timestamp) {
        String payload = String.format(
            "{\"imei\":\"%s\",\"lat\":%f,\"lon\":%f,\"timestamp\":\"%s\"}",
            imei, lat, lon, timestamp
        );
        kafkaTemplate.send(topic, imei, payload);
        log.info("Published GPS event to Kafka topic='{}' key='{}'", topic, imei);
    }
}
