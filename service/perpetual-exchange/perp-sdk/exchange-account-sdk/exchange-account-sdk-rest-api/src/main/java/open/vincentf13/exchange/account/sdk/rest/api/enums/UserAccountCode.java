package open.vincentf13.exchange.account.sdk.rest.api.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserAccountCode {
    SPOT("SPOT", "現貨資產", AccountCategory.ASSET),
    SPOT_EQUITY("SPOT_EQUITY", "現貨權益", AccountCategory.EQUITY),
    MARGIN("MARGIN", "逐倉保證金", AccountCategory.ASSET),
    FEE_EXPENSE("FEE_EXPENSE", "交易手續費支出", AccountCategory.EXPENSE),
    FEE_EQUITY("FEE_EQUITY", "交易手續費權益", AccountCategory.EQUITY),
    REALIZED_PNL_EQUITY("REALIZED_PNL_EQUITY", "已實現盈虧", AccountCategory.EQUITY),
    REALIZED_PNL_REVENUE("REALIZED_PNL_REVENUE", "已實現盈虧收入", AccountCategory.REVENUE);
    
    private final String code;
    private final String displayName;
    private final AccountCategory category;
}
