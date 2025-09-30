package com.example.exchange.web.config;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class BuildPropertiesConfiguration {

    @Bean
    @ConditionalOnMissingBean(BuildProperties.class)
    public BuildProperties buildProperties(Environment environment) {
        Properties properties = new Properties();
        properties.put("image.tag", environment.getProperty("image.tag", "local"));
        properties.put("build.timestamp", resolveTimestamp(environment));
        return new BuildProperties(properties);
    }

    private String resolveTimestamp(Environment environment) {
        String explicitTimestamp = environment.getProperty("build.timestamp");
        if (explicitTimestamp != null && !explicitTimestamp.isBlank()) {
            return explicitTimestamp;
        }
        explicitTimestamp = environment.getProperty("maven.build.timestamp");
        if (explicitTimestamp != null && !explicitTimestamp.isBlank()) {
            return explicitTimestamp;
        }
        String pattern = environment.getProperty("maven.build.timestamp.format", "yyyy-MM-dd HH:mm:ss");
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return LocalDateTime.now(ZoneId.systemDefault()).format(formatter);
        } catch (IllegalArgumentException ex) {
            return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now());
        }
    }
}
