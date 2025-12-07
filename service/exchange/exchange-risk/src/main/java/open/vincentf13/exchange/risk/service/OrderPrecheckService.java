package open.vincentf13.exchange.risk.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.risk.domain.model.RiskLimit;
import open.vincentf13.exchange.risk.infra.cache.InstrumentCache;
import open.vincentf13.exchange.risk.infra.cache.MarkPriceCache;
import open.vincentf13.exchange.risk.sdk.rest.api.OrderPrecheckRequest;
import open.vincentf13.exchange.risk.sdk.rest.api.OrderPrecheckResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderPrecheckService {

    private final InstrumentCache instrumentCache;
    private final MarkPriceCache markPriceCache;
    private final RiskLimitQueryService riskLimitQueryService;

    private static final BigDecimal BUFFER = BigDecimal.ZERO;

    public OrderPrecheckResponse precheck(OrderPrecheckRequest request) {
        Optional<InstrumentSummaryResponse> instrumentOpt = instrumentCache.get(request.getInstrumentId());
        if (instrumentOpt.isEmpty()) {
            return response(false, null, null, "Instrument not found");
        }
        InstrumentSummaryResponse instrument = instrumentOpt.get();
        if (!Boolean.TRUE.equals(instrument.tradable())) {
            return response(false, null, null, "Instrument is not tradable");
        }
        
        try {
            RiskLimit riskLimit = riskLimitQueryService.getRiskLimitByInstrumentId(request.getInstrumentId());
            OrderPrecheckRequest.PositionSnapshot snapshot = request.getPositionSnapshot();

            BigDecimal multiplier = instrument.contractSize() != null ? instrument.contractSize() : BigDecimal.ONE;
            BigDecimal markPrice = markPriceCache.get(request.getInstrumentId())
                    .orElse(snapshot.getMarkPrice() != null ? snapshot.getMarkPrice() : BigDecimal.ZERO);

            BigDecimal price = request.getPrice() != null ? request.getPrice() : markPrice;

            BigDecimal orderNotional = request.getQuantity().multiply(price).multiply(multiplier);

         

            BigDecimal requiredMargin = BigDecimal.ZERO;
            BigDecimal fee = orderNotional.multiply(instrument.takerFeeRate());

            if (request.getIntent() == PositionIntentType.INCREASE) {
                Integer leverage = snapshot.getLeverage() != null ? snapshot.getLeverage() : riskLimit.getMaxLeverage();
                if (leverage > riskLimit.getMaxLeverage()) {
                    return response(false, null, null, "Leverage exceeds limit");
                }

                BigDecimal leverageBd = BigDecimal.valueOf(leverage);
                // initial margin = orderNotional / leverage
                BigDecimal marginByLeverage = orderNotional.divide(leverageBd, 8, RoundingMode.HALF_UP);
                // initial margin rate check
                BigDecimal marginByRate = orderNotional.multiply(riskLimit.getInitialMarginRate());

                requiredMargin = marginByLeverage.max(marginByRate);
            }

            BigDecimal margin = snapshot.getMargin() != null ? snapshot.getMargin() : BigDecimal.ZERO;
            BigDecimal upnl = snapshot.getUnrealizedPnl() != null ? snapshot.getUnrealizedPnl() : BigDecimal.ZERO;
            BigDecimal currentEquity = margin.add(upnl);

            BigDecimal simulatedEquity = currentEquity.add(requiredMargin).subtract(fee);

            BigDecimal currentQty = snapshot.getQuantity() != null ? snapshot.getQuantity() : BigDecimal.ZERO;
            BigDecimal currentNotional = currentQty.abs().multiply(markPrice).multiply(multiplier);
            BigDecimal simulatedNotional;

            if (request.getIntent() == PositionIntentType.INCREASE) {
                simulatedNotional = currentNotional.add(orderNotional);
            } else {
                simulatedNotional = currentNotional.subtract(orderNotional);
                if (simulatedNotional.compareTo(BigDecimal.ZERO) < 0) simulatedNotional = BigDecimal.ZERO;
            }

            if (simulatedNotional.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal simulatedMarginRatio = simulatedEquity.divide(simulatedNotional, 8, RoundingMode.HALF_UP);
                BigDecimal mmr = riskLimit.getMaintenanceMarginRate().add(BUFFER);

                if (simulatedMarginRatio.compareTo(mmr) < 0) {
                     return response(false, requiredMargin, fee, "Insufficient margin: Risk of immediate liquidation");
                }
            } else {
                 if (simulatedEquity.compareTo(BigDecimal.ZERO) < 0) {
                     return response(false, requiredMargin, fee, "Insufficient margin: Bankruptcy risk");
                 }
            }

            return response(true, requiredMargin, fee, null);

        } catch (Exception e) {
            return response(false, null, null, "Risk check error: " + e.getMessage());
        }
    }

    private OrderPrecheckResponse response(boolean allow, BigDecimal reqMargin, BigDecimal fee, String reason) {
        return new OrderPrecheckResponse(allow, reqMargin, fee, reason);
    }
}
