package open.vincentf13.exchange.risk.margin.sdk.rest.api;

import java.math.BigDecimal;

public record LeveragePrecheckResponse(
        boolean allow,
        BigDecimal deficit,
        Integer suggestedLeverage,
        String reason
) {}
