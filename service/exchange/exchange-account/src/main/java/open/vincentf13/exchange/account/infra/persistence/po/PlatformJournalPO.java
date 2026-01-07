package open.vincentf13.exchange.account.infra.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.account.sdk.rest.api.enums.AccountCategory;
import open.vincentf13.exchange.account.sdk.rest.api.enums.ReferenceType;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.Direction;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("platform_journal")
public class PlatformJournalPO {
    
    @TableId(value = "journal_id", type = IdType.INPUT)
    private Long journalId;
    private Long accountId;
    private AccountCategory category;
    private AssetSymbol asset;
    private BigDecimal amount;
    private Direction direction;
    private BigDecimal balanceAfter;
    private ReferenceType referenceType;
    private String referenceId;
    private Integer seq;
    private String description;
    private Instant eventTime;
    private Instant createdAt;
}
