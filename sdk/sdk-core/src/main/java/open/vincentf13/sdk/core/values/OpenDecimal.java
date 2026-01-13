package open.vincentf13.sdk.core.values;

import java.math.BigDecimal;

/** Common BigDecimal helpers shared across services. */
public final class OpenDecimal {

  private OpenDecimal() {}
  
  public static BigDecimal normalizeDecimal(BigDecimal value) {
    return value == null ? null : value.stripTrailingZeros();
  }
}
