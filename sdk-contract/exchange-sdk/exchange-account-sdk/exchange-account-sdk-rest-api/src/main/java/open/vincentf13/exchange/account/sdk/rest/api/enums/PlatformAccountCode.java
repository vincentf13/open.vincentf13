package open.vincentf13.exchange.account.sdk.rest.api.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlatformAccountCode {
    HOT_WALLET("HOT_WALLET", "熱錢包"),
    USER_LIABILITY("USER_LIABILITY", "用戶存款負債");
    
    private final String code;
    private final String displayName;
}
