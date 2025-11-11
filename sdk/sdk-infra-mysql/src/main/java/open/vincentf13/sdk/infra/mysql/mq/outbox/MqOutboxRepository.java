package open.vincentf13.sdk.infra.mysql.mq.outbox;

import com.github.yitter.idgen.DefaultIdGenerator;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import open.vincentf13.sdk.core.OpenObjectMapper;

@Repository
@RequiredArgsConstructor
public class MqOutboxRepository {

    private final MqOutboxMapper mapper;
    private final DefaultIdGenerator idGenerator;
    @Transactional
    public <T> void append(String topic, Long key, T message, Map<String, Object> headers) {
        Assert.hasText(topic, "topic must not be blank");
        Assert.notNull(key, "key must not be null");
        Assert.notNull(message, "message must not be null");

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
