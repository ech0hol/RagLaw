package com.raglaw.rag.messaging;

import com.raglaw.rag.config.RagProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
@ConditionalOnProperty(prefix = "raglaw.rag.rabbit", name = "enabled", havingValue = "true")
public class RabbitMqConfig {

    @Bean
    public Queue parseQueue(RagProperties ragProperties) {
        return new Queue(ragProperties.getRabbit().getParseQueue(), true);
    }
}
