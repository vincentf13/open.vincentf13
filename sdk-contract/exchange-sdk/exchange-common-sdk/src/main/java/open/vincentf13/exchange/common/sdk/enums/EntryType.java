package open.vincentf13.exchange.common.sdk.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.common.sdk.enums.ReferenceType;

@Getter
@RequiredArgsConstructor
public enum EntryType {
    DEPOSIT(ReferenceType.DEPOSIT),
    WITHDRAWAL(ReferenceType.WITHDRAWAL),
    FREEZE(ReferenceType.ORDER),
    RESERVED(ReferenceType.ORDER),
    TRADE_SETTLEMENT_SPOT_MAIN(ReferenceType.TRADE),
    TRADE_SETTLEMENT_SPOT_MAIN_PNL(ReferenceType.TRADE),
    TRADE_SETTLEMENT_ISOLATED_MARGIN(ReferenceType.TRADE),
    FEE(ReferenceType.TRADE),
    TRADE(ReferenceType.TRADE);

    private final ReferenceType referenceType;

    public String code() {
        return name();
    }
}
