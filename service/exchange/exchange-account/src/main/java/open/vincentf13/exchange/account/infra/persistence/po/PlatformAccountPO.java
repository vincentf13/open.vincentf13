package open.vincentf13.exchange.account.infra.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.account.sdk.rest.api.enums.AccountCategory;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;

import java.time.Instant;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("platform_accounts")
public class PlatformAccountPO {
    
    @TableId(value = "account_id", type = IdType.INPUT)
    private Long accountId;
    private String accountCode;
    private String accountName;
    private AccountCategory category;
    private AssetSymbol asset;
    private BigDecimal balance;
    private Integer version;
    private Instant updatedAt;
    private Instant createdAt;
}
