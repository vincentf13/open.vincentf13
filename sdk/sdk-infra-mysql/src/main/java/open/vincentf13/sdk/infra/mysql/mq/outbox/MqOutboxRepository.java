package open.vincentf13.sdk.infra.mysql.mq.outbox;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import open.vincentf13.sdk.core.OpenValidator;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class MqOutboxRepository {

    private final MqOutboxMapper mapper;
    private final DefaultIdGenerator idGenerator;

    public <T> void append(String topic, Long key, T message, Map<String, Object> headers) {
        Assert.hasText(topic, "topic must not be blank");
        Assert.notNull(key, "key must not be null");
        Assert.notNull(message, "message must not be null");
        OpenValidator.validateOrThrow(message);

        String eventId = String.valueOf(idGenerator.newLong());

        MqOutboxPO record = MqOutboxPO.builder()
                .eventId(eventId)
                .aggregateType(topic)
                .aggregateId(key)
                .eventType(message.getClass().getName())
                .payload(OpenObjectMapper.toJson(message))
                .headers(writeHeaders(headers))
                .createdAt(Instant.now())
                .build();

        mapper.insertWithAutoSeq(record);
    }

    private String writeHeaders(Map<String, Object> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        return OpenObjectMapper.toJson(headers);
    }
}
