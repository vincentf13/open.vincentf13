package open.vincentf13.sdk.devtool.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

import java.lang.reflect.Type;
@Slf4j
public class DoubleDecodingJsonMessageConverter extends StringJsonMessageConverter {

        public DoubleDecodingJsonMessageConverter(ObjectMapper objectMapper) {
            super(objectMapper);
        }
    
        @Override
        protected Object extractAndConvertValue(ConsumerRecord<?, ?> record, Type type) {
            Object value = record.value();
            if (value instanceof String stringValue) {            String trimmed = stringValue.trim();
            // Basic heuristic: starts with quote -> likely double encoded JSON string
            if (trimmed.startsWith("\"")) {
                try {
                    // First decode: JSON String -> Raw String
                    // e.g. "{\"a\":1}" -> {"a":1}
                    String innerJson = this.getObjectMapper().readValue(trimmed, String.class);
                    // Second decode: Raw String -> Target Type
                    return this.getObjectMapper().readValue(innerJson, this.getObjectMapper().constructType(type));
                } catch (Exception e) {
                    // If it fails (e.g. it wasn't double encoded, or inner json is invalid),
                    // fall back to standard behavior (try to parse the original string as the object)
                }
            }
        }
        return super.extractAndConvertValue(record, type);
    }
}
