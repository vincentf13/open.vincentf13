package open.vincentf13.common.infra.kafka.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import open.vincentf13.common.infra.kafka.OpenKafkaProducer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.kafka.core.KafkaTemplate;


@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
public class ConfigKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // 透過構造函數注入由 Spring Boot 自動配置好的 KafkaTemplate
    public ConfigKafkaProducer(KafkaTemplate<String, Object> kafkaTemplate, @Qualifier("openObjectMapper") ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initializeOpenKafkaProducer() {
        // 使用正確的 KafkaTemplate 初始化我們的工具類
        OpenKafkaProducer.initialize(kafkaTemplate, objectMapper);
    }
}