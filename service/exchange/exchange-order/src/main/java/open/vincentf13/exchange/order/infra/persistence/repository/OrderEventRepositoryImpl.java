package open.vincentf13.exchange.order.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.order.domain.model.OrderEvent;
import open.vincentf13.exchange.order.infra.persistence.mapper.OrderEventMapper;
import open.vincentf13.exchange.order.infra.persistence.po.OrderEventPO;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class OrderEventRepositoryImpl implements OrderEventRepository {

    private final OrderEventMapper mapper;
    private final DefaultIdGenerator idGenerator;

    @Override
    public void append(OrderEvent event) {
        OrderEvent eventToPersist = ensureIdentifiers(event);
        OrderEventPO po = OpenMapstruct.map(eventToPersist, OrderEventPO.class);
        mapper.insert(po);
    }

    @Override
    public List<OrderEvent> findByOrderId(Long orderId) {
        return mapper.findByOrderId(orderId).stream()
                .map(po -> OpenMapstruct.map(po, OrderEvent.class))
                .toList();
    }

    @Override
    public long nextSequence(Long orderId) {
        Long next = mapper.nextSequence(orderId);
        return next != null ? next : 1L;
    }

    @Override
    public boolean existsByReference(Long orderId, String referenceType, Long referenceId) {
        Integer count = mapper.countByReference(orderId, referenceType, referenceId);
        return count != null && count > 0;
    }

    private OrderEvent ensureIdentifiers(OrderEvent event) {
        OrderEvent.OrderEventBuilder builder = event.toBuilder();
        if (event.getEventId() == null) {
            builder.eventId(idGenerator.newLong());
        }
        if (event.getCreatedAt() == null) {
            builder.createdAt(Instant.now());
        }
        if (event.getSequenceNumber() == null) {
            builder.sequenceNumber(nextSequence(event.getOrderId()));
        }
        if (event.getOccurredAt() == null) {
            builder.occurredAt(Instant.now());
        }
        return builder.build();
    }
}
