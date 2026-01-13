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
@TableName("kline_buckets")
public class KlineBucketPO {

  @TableId(value = "bucket_id", type = IdType.INPUT)
  private Long bucketId;

  private Long instrumentId;
  private String period;
  private Instant bucketStart;
  private Instant bucketEnd;
  private BigDecimal openPrice;
  private BigDecimal highPrice;
  private BigDecimal lowPrice;
  private BigDecimal closePrice;
  private BigDecimal volume;
  private BigDecimal turnover;
  private Integer tradeCount;
  private BigDecimal takerBuyVolume;
  private BigDecimal takerBuyTurnover;
  private Boolean isClosed;
  private Instant createdAt;
  private Instant updatedAt;
}
