package open.vincentf13.exchange.risk.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.risk.domain.model.RiskLimit;
import open.vincentf13.exchange.risk.infra.cache.InstrumentCache;
import open.vincentf13.exchange.risk.infra.cache.MarkPriceCache;
import open.vincentf13.exchange.risk.sdk.rest.api.OrderPrecheckRequest;
import open.vincentf13.exchange.risk.sdk.rest.api.OrderPrecheckResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class OrderPrecheckService {
    
    private final InstrumentCache instrumentCache;
    private final MarkPriceCache markPriceCache;
    private final RiskLimitQueryService riskLimitQueryService;
    
    private static final BigDecimal BUFFER = BigDecimal.ZERO;
    
    public OrderPrecheckResponse precheck(OrderPrecheckRequest request) {
        // 1. 基礎校驗 (Fail Fast)
        var instrumentOpt = instrumentCache.get(request.getInstrumentId());
        if (instrumentOpt.isEmpty())
            return error("Instrument not found");
        
        var instrument = instrumentOpt.get();
        if (!Boolean.TRUE.equals(instrument.tradable()))
            return error("Instrument is not tradable");
        
        try {
            // 2. 數據上下文準備
            var riskLimit = riskLimitQueryService.getRiskLimitByInstrumentId(request.getInstrumentId());
            var snapshot = request.getPositionSnapshot();
            
            BigDecimal markPrice = resolvePrice(request.getInstrumentId(), snapshot.getMarkPrice());
            BigDecimal execPrice = request.getPrice() != null ? request.getPrice() : markPrice;
            BigDecimal multiplier = instrument.contractSize();
            
            // 3. 計算訂單維度數據
            BigDecimal orderNotional = request.getQuantity().multiply(execPrice).multiply(multiplier);
            BigDecimal fee = orderNotional.multiply(instrument.takerFeeRate());
            
            // 4. 初始保證金與槓桿檢查 (Initial Margin Check)
            BigDecimal requiredMargin = BigDecimal.ZERO;
            if (request.getIntent() == PositionIntentType.INCREASE) {
                if (snapshot.getLeverage() > riskLimit.getMaxLeverage()) {
                    return error("Leverage exceeds limit");
                }
                requiredMargin = calculateInitialMargin(orderNotional, snapshot.getLeverage(), riskLimit);
            }
            
            // 5. 模擬交易後狀態 (Post-Trade Simulation)
            return validateLiquidationRisk(snapshot, requiredMargin, fee, orderNotional, markPrice, multiplier, riskLimit, request.getIntent());
            
        } catch (Exception e) {
            return error("Risk check error: " + e.getMessage());
        }
    }
    
    
    private BigDecimal resolvePrice(Long instrumentId,
                                    BigDecimal snapshotPrice) {
        return markPriceCache.get(instrumentId)
                             .orElse(snapshotPrice != null ? snapshotPrice : BigDecimal.ZERO);
    }
    
    private BigDecimal calculateInitialMargin(BigDecimal notional,
                                              Integer leverage,
                                              RiskLimit riskLimit) {
        BigDecimal leverageBd = BigDecimal.valueOf(leverage);
        BigDecimal marginByLeverage = notional.divide(leverageBd, 8, RoundingMode.HALF_UP);
        BigDecimal marginByRate = notional.multiply(riskLimit.getInitialMarginRate());
        return marginByLeverage.max(marginByRate);
    }
    
    private OrderPrecheckResponse validateLiquidationRisk(
            OrderPrecheckRequest.PositionSnapshot snapshot,
            BigDecimal requiredMargin,
            BigDecimal fee,
            BigDecimal orderNotional,
            BigDecimal markPrice,
            BigDecimal multiplier,
            RiskLimit riskLimit,
            PositionIntentType intent) {
        
        BigDecimal currentMargin = snapshot.getMargin() != null ? snapshot.getMargin() : BigDecimal.ZERO;
        BigDecimal upnl = snapshot.getUnrealizedPnl() != null ? snapshot.getUnrealizedPnl() : BigDecimal.ZERO;
        
        // 計算模擬權益
        BigDecimal simulatedEquity = currentMargin.add(upnl).add(requiredMargin).subtract(fee);
        
        // 計算模擬名義價值
        BigDecimal currentQty = snapshot.getQuantity() != null ? snapshot.getQuantity() : BigDecimal.ZERO;
        BigDecimal currentNotional = currentQty.abs().multiply(markPrice).multiply(multiplier);
        
        BigDecimal simulatedNotional;
        if (intent == PositionIntentType.INCREASE) {
            simulatedNotional = currentNotional.add(orderNotional);
        } else {
            simulatedNotional = currentNotional.subtract(orderNotional).max(BigDecimal.ZERO);
        }
        
        // 強平風險檢查邏輯
        if (simulatedNotional.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal simulatedMarginRatio = simulatedEquity.divide(simulatedNotional, 8, RoundingMode.HALF_UP);
            BigDecimal mmrRequirement = riskLimit.getMaintenanceMarginRate().add(BUFFER);
            
            // 成交就爆倉 -> 拒絕
            if (simulatedMarginRatio.compareTo(mmrRequirement) < 0) {
                return response(false, requiredMargin, fee, "Insufficient margin: Risk of immediate liquidation");
            }
        } else {
            // 完全平倉
            
            // 若虧損到不夠付手續費，不讓平倉
            if (simulatedEquity.compareTo(BigDecimal.ZERO) < 0) {
                return response(false, requiredMargin, fee, "Insufficient margin: Bankruptcy risk");
            }
        }
        
        return response(true, requiredMargin, fee, null);
    }
    
    private OrderPrecheckResponse error(String msg) {
        return response(false, null, null, msg);
    }
    
    private OrderPrecheckResponse response(boolean allow,
                                           BigDecimal reqMargin,
                                           BigDecimal fee,
                                           String reason) {
        return new OrderPrecheckResponse(allow, reqMargin, fee, reason);
    }
}
