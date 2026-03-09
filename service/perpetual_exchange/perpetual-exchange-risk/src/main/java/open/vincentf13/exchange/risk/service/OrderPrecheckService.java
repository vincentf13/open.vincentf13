package open.vincentf13.exchange.risk.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.sdk.rest.client.ExchangeAccountClient;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.risk.domain.model.RiskLimit;
import open.vincentf13.exchange.risk.infra.cache.InstrumentCache;
import open.vincentf13.exchange.risk.infra.cache.MarkPriceCache;
import open.vincentf13.exchange.risk.sdk.rest.api.OrderPrecheckRequest;
import open.vincentf13.exchange.risk.sdk.rest.api.OrderPrecheckResponse;
import open.vincentf13.sdk.spring.cloud.openfeign.OpenApiClientInvoker;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderPrecheckService {

  private static final BigDecimal BUFFER = BigDecimal.ZERO;
  private static final String NEGATIVE_BALANCE_REASON =
      "因訂單成交後倉位flip，導致平倉訂單成交後轉開倉，扣減保證金後餘額不足，導致餘額變成負數。請聯繫補充資金或聯繫客服了解詳情。[正式上線應該要做強制平倉，但這裡不省略，僅顯示此提示信息]";
  private final InstrumentCache instrumentCache;
  private final MarkPriceCache markPriceCache;
  private final RiskLimitQueryService riskLimitQueryService;
  private final ExchangeAccountClient exchangeAccountClient;

  public OrderPrecheckResponse precheck(OrderPrecheckRequest request) {
    // 1. 基礎校驗 (Fail Fast)
    var instrumentOpt = instrumentCache.get(request.getInstrumentId());
    if (instrumentOpt.isEmpty())
      return new OrderPrecheckResponse(false, null, null, "Instrument not found");

    var instrument = instrumentOpt.get();
    if (!Boolean.TRUE.equals(instrument.tradable()))
      return new OrderPrecheckResponse(false, null, null, "Instrument is not tradable");

    try {

      // 餘額檢查
      if (request.getUserId() != null && instrument.quoteAsset() != null) {
        var balanceResponse =
            OpenApiClientInvoker.call(
                () ->
                    exchangeAccountClient.getBalances(
                        instrument.quoteAsset().code(), request.getUserId()));
        var balanceItem = balanceResponse.balances();
        if (balanceItem != null
            && ((balanceItem.balance() != null && balanceItem.balance().signum() < 0)
                || (balanceItem.available() != null && balanceItem.available().signum() < 0))) {
          return new OrderPrecheckResponse(false, null, null, NEGATIVE_BALANCE_REASON);
        }
      }
      // 2. 數據上下文準備
      var riskLimit = riskLimitQueryService.getRiskLimitByInstrumentId(request.getInstrumentId());
      Integer defaultLeverage = instrument.defaultLeverage();
      var snapshot =
          normalizeSnapshot(
              request.getPositionSnapshot(), request.getInstrumentId(), defaultLeverage);

      BigDecimal markPrice = snapshot.getMarkPrice();
      BigDecimal execPrice = request.getPrice() != null ? request.getPrice() : markPrice;
      BigDecimal multiplier = instrument.contractSize();

      // 3. 計算訂單維度數據
      BigDecimal orderNotional = request.getQuantity().multiply(execPrice).multiply(multiplier);
      BigDecimal fee = orderNotional.multiply(instrument.takerFeeRate());

      // 4. 初始保證金與槓桿檢查 (Initial Margin Check)
      BigDecimal requiredMargin = BigDecimal.ZERO;
      if (request.getIntent() == PositionIntentType.INCREASE) {
        if (Objects.compare(snapshot.getLeverage(), riskLimit.getMaxLeverage(), Integer::compare)
            > 0) {
          return new OrderPrecheckResponse(false, null, null, "Leverage exceeds limit");
        }
        requiredMargin = calculateInitialMargin(orderNotional, snapshot.getLeverage(), riskLimit);
      }

      // 5. 模擬交易後狀態 (Post-Trade Simulation)
      return validateLiquidationRisk(
          snapshot,
          requiredMargin,
          fee,
          orderNotional,
          markPrice,
          multiplier,
          riskLimit,
          request.getIntent());

    } catch (Exception e) {
      e.printStackTrace();
      return new OrderPrecheckResponse(false, null, null, "Risk check error: " + e.getMessage());
    }
  }

  private OrderPrecheckRequest.PositionSnapshot normalizeSnapshot(
      OrderPrecheckRequest.PositionSnapshot snapshot, Long instrumentId, Integer defaultLeverage) {
    OrderPrecheckRequest.PositionSnapshot normalized =
        snapshot != null ? snapshot : new OrderPrecheckRequest.PositionSnapshot();
    if (normalized.getLeverage() == null || normalized.getLeverage() <= 0) {
      Integer leverage = defaultLeverage != null && defaultLeverage > 0 ? defaultLeverage : 1;
      normalized.setLeverage(leverage);
    }
    if (normalized.getMargin() == null) {
      normalized.setMargin(BigDecimal.ZERO);
    }
    if (normalized.getQuantity() == null) {
      normalized.setQuantity(BigDecimal.ZERO);
    }
    if (normalized.getUnrealizedPnl() == null) {
      normalized.setUnrealizedPnl(BigDecimal.ZERO);
    }
    if (normalized.getMarkPrice() == null
        || normalized.getMarkPrice().compareTo(BigDecimal.ZERO) <= 0) {
      normalized.setMarkPrice(markPriceCache.get(instrumentId).orElse(BigDecimal.ZERO));
    }
    return normalized;
  }

  private BigDecimal calculateInitialMargin(
      BigDecimal notional, Integer leverage, RiskLimit riskLimit) {
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

    // 計算模擬權益
    BigDecimal simulatedEquity =
        snapshot.getMargin().add(snapshot.getUnrealizedPnl()).add(requiredMargin);
    // 權益模擬不扣手續費
    // 避免第一次開倉時，若初始保證金/維持保證金率 都設定 X%， 扣掉手續費後 模擬保證金低於 X%，誤認為開倉就爆倉而被風控的情況。
    // 後續凍結資金時，若餘額不足會自動擋下。
    // .subtract(fee);

    // 計算模擬名義價值
    BigDecimal currentNotional =
        snapshot.getQuantity().abs().multiply(markPrice).multiply(multiplier);

    BigDecimal simulatedNotional;
    if (intent == PositionIntentType.INCREASE) {
      simulatedNotional = currentNotional.add(orderNotional);
    } else {
      simulatedNotional = currentNotional.subtract(orderNotional).max(BigDecimal.ZERO);
    }

    // 強平風險檢查邏輯
    if (simulatedNotional.compareTo(BigDecimal.ZERO) > 0) {
      BigDecimal simulatedMarginRatio =
          simulatedEquity.divide(simulatedNotional, 8, RoundingMode.HALF_UP);
      BigDecimal mmrRequirement = riskLimit.getMaintenanceMarginRate().add(BUFFER);

      // 成交就爆倉 -> 拒絕
      if (simulatedMarginRatio.compareTo(mmrRequirement) < 0) {
        return new OrderPrecheckResponse(
            false, requiredMargin, fee, "Insufficient margin: Risk of immediate liquidation");
      }
    } else {
      // 平倉

      // 若虧損到不夠付手續費，不讓平倉
      if (simulatedEquity.compareTo(BigDecimal.ZERO) < 0) {
        return new OrderPrecheckResponse(
            false, requiredMargin, fee, "Insufficient margin: Bankruptcy risk");
      }
    }

    return new OrderPrecheckResponse(true, requiredMargin, fee, null);
  }
}
