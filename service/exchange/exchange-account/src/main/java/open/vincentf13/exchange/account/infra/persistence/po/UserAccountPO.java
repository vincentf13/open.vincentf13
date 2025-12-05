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

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_accounts")
public class UserAccountPO {
    
    @TableId(value = "account_id", type = IdType.INPUT)
    private Long accountId;
    private Long userId;
    private String accountCode;
    private String accountName;
    private Long instrumentId;
    private AccountCategory category;
    private AssetSymbol asset;
    private BigDecimal balance;
    private BigDecimal available;
    private BigDecimal reserved;
    private Integer version;
    private Instant updatedAt;
    private Instant createdAt;
}
