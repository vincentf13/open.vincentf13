package open.vincentf13.exchange.account.ledger.infra.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.PlatformAccountCategory;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.PlatformAccountCode;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.PlatformAccountStatus;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("platform_accounts")
public class PlatformAccountPO {

    @TableId(value = "account_id", type = IdType.INPUT)
    private Long accountId;
    private PlatformAccountCode accountCode;
    private PlatformAccountCategory category;
    private String name;
    private PlatformAccountStatus status;
    private Instant createdAt;
}
