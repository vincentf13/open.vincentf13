package open.vincentf13.exchange.account.sdk.rest.api.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserAccountCode {
    SPOT("SPOT", "現貨資產"),
    SPOT_EQUITY("SPOT_EQUITY", "現貨權益");
    
    private final String code;
    private final String displayName;
}
