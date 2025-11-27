package open.vincentf13.exchange.account.ledger.infra.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.PlatformAccountCode;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("platform_balances")
public class PlatformBalancePO {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private Long accountId;
    private PlatformAccountCode accountCode;
    private AssetSymbol asset;
    private BigDecimal balance;
    private BigDecimal reserved;
    private Integer version;
    private Long lastEntryId;
    private Instant createdAt;
    private Instant updatedAt;
}
