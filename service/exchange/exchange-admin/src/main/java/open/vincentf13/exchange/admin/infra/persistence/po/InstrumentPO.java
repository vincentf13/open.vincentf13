package open.vincentf13.exchange.admin.infra.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.admin.contract.enums.InstrumentStatus;
import open.vincentf13.exchange.admin.contract.enums.InstrumentType;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("instrument")
public class InstrumentPO {
    
    @TableId(value = "instrument_id", type = IdType.INPUT)
    private Long instrumentId;
    
    private String symbol;
    private AssetSymbol baseAsset;
    private AssetSymbol quoteAsset;
    private InstrumentType instrumentType;
    private InstrumentStatus status;
    private BigDecimal makerFeeRate;
    private BigDecimal takerFeeRate;
    private BigDecimal contractSize;
    private Instant launchAt;
    private Instant delistAt;
    private Integer displayOrder;
    @TableField("is_tradable")
    private Boolean tradable;
    @TableField("is_visible")
    private Boolean visible;
    private String description;
    private String metadata;
    private Instant createdAt;
    private Instant updatedAt;
}
