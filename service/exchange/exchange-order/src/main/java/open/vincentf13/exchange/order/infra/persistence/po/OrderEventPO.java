package open.vincentf13.exchange.order.infra.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderEventReferenceType;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderEventType;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("order_events")
public class OrderEventPO {

    @TableId(value = "event_id", type = IdType.INPUT)
    private Long eventId;
    private Long userId;
    private Long instrumentId;
    private Long orderId;
    private OrderEventType eventType;
    private Long sequenceNumber;
    private String payload;
    private OrderEventReferenceType referenceType;
    private Long referenceId;
    private String actor;
    private Instant occurredAt;
    private Instant createdAt;
}
