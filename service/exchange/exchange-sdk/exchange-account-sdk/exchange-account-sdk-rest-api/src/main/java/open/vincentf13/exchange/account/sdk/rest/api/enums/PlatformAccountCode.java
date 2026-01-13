package open.vincentf13.exchange.account.sdk.rest.api.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlatformAccountCode {
  HOT_WALLET("HOT_WALLET", "熱錢包", AccountCategory.ASSET),
  USER_LIABILITY("USER_LIABILITY", "用戶存款負債", AccountCategory.LIABILITY),
  TRADING_FEE_REVENUE("TRADING_FEE_REVENUE", "交易手續費收入", AccountCategory.REVENUE),
  TRADING_FEE_EQUITY("TRADING_FEE_EQUITY", "交易手續費權益", AccountCategory.EQUITY);

  private final String code;
  private final String displayName;
  private final AccountCategory category;
}
