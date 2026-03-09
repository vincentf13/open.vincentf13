package open.vincentf13.exchange.market.infra.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
  private BigDecimal markPriceChangeRate;
  private Long tradeId;
  private Instant tradeExecutedAt;
  private Instant calculatedAt;
  private Instant createdAt;
  private Instant updatedAt;
}
