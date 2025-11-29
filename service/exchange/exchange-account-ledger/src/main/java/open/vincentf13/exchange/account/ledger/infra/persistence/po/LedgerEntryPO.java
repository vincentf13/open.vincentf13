package open.vincentf13.exchange.account.ledger.infra.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.common.sdk.enums.Direction;
import open.vincentf13.exchange.common.sdk.enums.EntryType;
import open.vincentf13.exchange.common.sdk.enums.OwnerType;
import open.vincentf13.exchange.common.sdk.enums.ReferenceType;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ledger_entries")
public class LedgerEntryPO {
    
    @TableId(value = "entry_id", type = IdType.INPUT)
    private Long entryId;
    private OwnerType ownerType;
    private Long accountId;
    private Long userId;
    private AssetSymbol asset;
    private BigDecimal amount;
    private Direction direction;
    private Long counterpartyEntryId;
    private BigDecimal balanceAfter;
    private ReferenceType referenceType;
    private String referenceId;
    private EntryType entryType;
    private String description;
    private String metadata;
    private Instant eventTime;
    private Instant createdAt;
}
