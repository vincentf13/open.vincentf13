package open.vincentf13.exchange.matching.infra.messaging.publisher;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.sdk.core.OpenValidator;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import open.vincentf13.sdk.infra.mysql.mq.outbox.MqOutboxMapper;
import open.vincentf13.sdk.infra.mysql.mq.outbox.MqOutboxPO;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class MatchingOutboxRepository {
    
    private final MqOutboxMapper mqOutboxMapper;
    private final DefaultIdGenerator idGenerator;
    
    public void append(String topic,
                       Long key,
                       Object payload,
                       Map<String, Object> headers,
                       long seq) {
        Assert.hasText(topic, "topic must not be blank");
        Assert.notNull(key, "aggregate key must not be null");
        Assert.notNull(payload, "payload must not be null");
        OpenValidator.validateOrThrow(payload);
        MqOutboxPO record = MqOutboxPO.builder()
                                      .eventId(String.valueOf(idGenerator.newLong()))
                                      .aggregateType(topic)
                                      .aggregateId(key)
                                      .eventType(payload.getClass().getName())
                                      .payload(OpenObjectMapper.toJson(payload))
                                      .headers(headers == null || headers.isEmpty() ? null : OpenObjectMapper.toJson(headers))
                                      .seq(seq)
                                      .createdAt(Instant.now())
                                      .build();
        mqOutboxMapper.insertSelective(record);
    }
}
