package open.vincentf13.exchange.market.infra.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("mark_price_snapshots")
public class MarkPriceSnapshotPO {
    
    @TableId(value = "snapshot_id", type = IdType.INPUT)
    private Long snapshotId;
    
    private Long instrumentId;
    private BigDecimal markPrice;
    private Long tradeId;
    private Instant tradeExecutedAt;
    private Instant calculatedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
