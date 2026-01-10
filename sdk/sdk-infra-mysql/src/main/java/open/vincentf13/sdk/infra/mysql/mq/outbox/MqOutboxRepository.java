package open.vincentf13.sdk.infra.mysql.mq.outbox;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.sdk.core.validator.OpenValidator;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class MqOutboxRepository {
    
    private final MqOutboxMapper mapper;
    private final DefaultIdGenerator idGenerator;
    
    public <T> void append(String topic,
                           Long key,
                           T message,
                           Map<String, Object> headers) {
        Assert.hasText(topic, "topic must not be blank");
        Assert.notNull(key, "key must not be null");
        Assert.notNull(message, "message must not be null");
        OpenValidator.validateOrThrow(message);
        
        String eventId = String.valueOf(idGenerator.newLong());
        
        MqOutboxPO record = MqOutboxPO.builder()
                                      .eventId(eventId)
                                      .aggregateType(topic)
                                      .aggregateId(key)
                                      .eventType(message.getClass().getSimpleName())
                                      .payload(OpenObjectMapper.toJson(message))
                                      .headers(writeHeaders(headers))
                                      .createdAt(Instant.now())
                                      .build();
        
        mapper.insertWithAutoSeq(record);
    }

    public <T> void appendWithSeq(String topic,
                                  Long key,
                                  T message,
                                  Map<String, Object> headers,
                                  Long seq) {
        Assert.hasText(topic, "topic must not be blank");
        Assert.notNull(key, "key must not be null");
        Assert.notNull(message, "message must not be null");
        OpenValidator.validateOrThrow(message);
        String eventId = String.valueOf(idGenerator.newLong());
        MqOutboxPO record = MqOutboxPO.builder()
                                      .eventId(eventId)
                                      .aggregateType(topic)
                                      .aggregateId(key)
                                      .eventType(message.getClass().getSimpleName())
                                      .payload(OpenObjectMapper.toJson(message))
                                      .headers(writeHeaders(headers))
                                      .seq(seq)
                                      .createdAt(Instant.now())
                                      .build();
        if (seq == null) {
            mapper.insertWithAutoSeq(record);
        } else {
            mapper.insertSelective(record);
        }
    }

    public boolean exists(String topic, Long key) {
        Assert.hasText(topic, "topic must not be blank");
        Assert.notNull(key, "key must not be null");
        MqOutboxPO criteria = MqOutboxPO.builder()
                                        .aggregateType(topic)
                                        .aggregateId(key)
                                        .build();
        return !mapper.findBy(criteria).isEmpty();
    }
    
    private String writeHeaders(Map<String, Object> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        return OpenObjectMapper.toJson(headers);
    }
}
