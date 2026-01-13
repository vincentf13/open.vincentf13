package open.vincentf13.exchange.order.infra.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.domain.model.OrderEventRecord;
import open.vincentf13.exchange.order.infra.persistence.mapper.OrderEventMapper;
import open.vincentf13.exchange.order.infra.persistence.po.OrderEventPO;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderEventReferenceType;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderEventType;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderEventRepository {

  private final OrderEventMapper mapper;
  private final DefaultIdGenerator idGenerator;

  public void append(
      @NotNull Order order,
      @NotNull OrderEventType eventType,
      @NotNull String actor,
      Instant occurredAt,
      Object payload,
      OrderEventReferenceType referenceType,
      String referenceId) {
    Long orderId = order.getOrderId();
    if (orderId == null) {
      throw new IllegalArgumentException("orderId is required when logging order event");
    }
    Long currentSeq = mapper.selectMaxSequenceForUpdate(orderId);
    long nextSeq = currentSeq == null ? 1L : currentSeq + 1L;
    String resolvedReferenceId = referenceId != null ? referenceId : String.valueOf(orderId);
    OrderEventPO event =
        OrderEventPO.builder()
            .eventId(idGenerator.newLong())
            .orderId(orderId)
            .userId(order.getUserId())
            .instrumentId(order.getInstrumentId())
            .eventType(eventType)
            .sequenceNumber(nextSeq)
            .payload(formatPayload(payload))
            .referenceType(referenceType)
            .referenceId(resolvedReferenceId)
            .actor(actor)
            .occurredAt(Objects.requireNonNullElseGet(occurredAt, Instant::now))
            .build();
    mapper.insert(event);
  }

  public boolean existsByReference(
      Long orderId, OrderEventReferenceType referenceType, String referenceId) {
    if (orderId == null || referenceType == null || referenceId == null) {
      return false;
    }
    long count = mapper.countByReference(orderId, referenceType, referenceId);
    return count > 0;
  }

  public List<OrderEventRecord> findByOrderId(@NotNull Long orderId) {
    var wrapper =
        Wrappers.<OrderEventPO>lambdaQuery()
            .eq(OrderEventPO::getOrderId, orderId)
            .orderByDesc(OrderEventPO::getSequenceNumber);
    return mapper.selectList(wrapper).stream()
        .map(po -> OpenObjectMapper.convert(po, OrderEventRecord.class))
        .filter(Objects::nonNull)
        .toList();
  }

  private String formatPayload(Object payload) {
    if (payload == null) {
      return "{}";
    }
    if (payload instanceof String) {
      return (String) payload;
    }
    return OpenObjectMapper.toJson(payload);
  }
}
