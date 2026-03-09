package open.vincentf13.exchange.account.sdk.mq.topic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.sdk.mq.event.FundsFreezeFailedEvent;
import open.vincentf13.exchange.account.sdk.mq.event.FundsFrozenEvent;

@Getter
@RequiredArgsConstructor
public enum AccountFundsTopics {
  FUNDS_FROZEN(Names.FUNDS_FROZEN, FundsFrozenEvent.class),
  FUNDS_FREEZE_FAILED(Names.FUNDS_FREEZE_FAILED, FundsFreezeFailedEvent.class);

  private final String topic;
  private final Class<?> eventType;

  public static final class Names {
    public static final String FUNDS_FROZEN = "account.funds-frozen";
    public static final String FUNDS_FREEZE_FAILED = "account.funds-freeze-failed";

    private Names() {}
  }
}
