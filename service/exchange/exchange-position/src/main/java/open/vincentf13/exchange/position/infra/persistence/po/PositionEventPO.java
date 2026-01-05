package open.vincentf13.exchange.position.infra.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionEventType;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionReferenceType;

import java.time.Instant;

@Data
@TableName("position_events")
public class PositionEventPO {
    @TableId(type = IdType.INPUT)
    private Long eventId;
    private Long positionId;
    private Long userId;
    private Long instrumentId;
    private PositionEventType eventType;
    private Long sequenceNumber;
    private String payload;
    private String referenceId;
    private PositionReferenceType referenceType;
    private Instant occurredAt;
    private Instant createdAt;
}
