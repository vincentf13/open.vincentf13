package open.vincentf13.exchange.position.infra.persistence.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionSide;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionPO {

    private Long positionId;
    private Long userId;
    private Long instrumentId;
    private Integer leverage;
    private BigDecimal margin;
    private PositionSide side;
    private BigDecimal entryPrice;
    private BigDecimal quantity;
    private BigDecimal closingReservedQuantity;
    private BigDecimal markPrice;
    private BigDecimal marginRatio;
    private BigDecimal unrealizedPnl;
    private BigDecimal realizedPnl;
    private BigDecimal liquidationPrice;
    private BigDecimal bankruptcyPrice;
    private String status;
    private Long lastTradeId;
    private Integer version;
    private Integer expectedVersion;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant closedAt;
}
