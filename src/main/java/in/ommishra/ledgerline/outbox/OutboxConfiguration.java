package in.ommishra.ledgerline.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
class OutboxConfiguration {

    /** Created on startup by Spring Kafka's admin if it does not exist yet. */
    @Bean
    NewTopic ledgerEventsTopic(OutboxProperties properties) {
        return TopicBuilder.name(properties.topic()).partitions(properties.partitions()).build();
    }
}
