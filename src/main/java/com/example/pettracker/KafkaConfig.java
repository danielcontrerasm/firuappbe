// com.example.pettracker.config.KafkaConfig
package com.example.pettracker;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

   /* @Bean
    public NewTopic gpsTopic(@Value("${gps.kafka.topic:gps-locations}") String topic) {
        // 1 partition, replication factor 1 for dev
        return new NewTopic(topic, 1, (short) 1);
    }*/
}
