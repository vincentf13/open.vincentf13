package open.vincentf13.exchange.test;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountBalanceItem;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountBalanceSheetResponse;
import open.vincentf13.exchange.account.sdk.rest.api.enums.UserAccountCode;
import open.vincentf13.exchange.admin.contract.dto.InstrumentDetailResponse;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.PositionSide;
import open.vincentf13.exchange.common.sdk.enums.PositionStatus;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionResponse;
import open.vincentf13.exchange.risk.sdk.rest.api.RiskLimitResponse;
import open.vincentf13.exchange.test.client.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@Slf4j
class TradeTest {
  private static final String GATEWAY_HOST = "http://localhost:12345";
  private static final int INSTRUMENT_ID = 10001;
  private static final String PASSWORD = "12345678";
  private static final String USER_A = "c.p.kevinf13@gmail.com";
  private static final String USER_B = "c.p.kevinf13-2@gmail.com";
  private static final String USER_C = "c.p.kevinf13-3@gmail.com";
  private static final long MAKER_DELAY_MS = 2000;
  private static final long TAKER_DELAY_MS = 3000;

  private Integer instrumentId;
  private String tokenA;
  private String tokenB;
  private String tokenC;
  private BigDecimal contractSize;
  private BigDecimal makerFeeRate;
  private BigDecimal takerFeeRate;
  private Integer leverage;
  private BigDecimal mmr;
  private BigDecimal imr;

  @BeforeEach
  public void setUp() {
    log.info(">>> Starting setUp");
    BaseClient.setHost(GATEWAY_HOST);

    log.info("Resetting system data...");
    SystemClient.resetData();

    log.info("Logging in users...");
    tokenA = AuthClient.login(USER_A, PASSWORD);
    tokenB = AuthClient.login(USER_B, PASSWORD);
    tokenC = AuthClient.login(USER_C, PASSWORD);

    log.info("Depositing initial funds...");
    AccountClient.deposit(tokenA, 10000);
    AccountClient.deposit(tokenB, 10000);
    AccountClient.deposit(tokenC, 10000);

    log.info("Loading instrument and risk limit details...");
    InstrumentDetailResponse instrument = AdminClient.getInstrument(INSTRUMENT_ID);
    RiskLimitResponse risk = RiskClient.getRiskLimit(INSTRUMENT_ID);

    contractSize = instrument.contractSize();
    makerFeeRate = instrument.makerFeeRate();
    takerFeeRate = instrument.takerFeeRate();
    leverage = instrument.defaultLeverage();
    mmr = risk.maintenanceMarginRate();
    imr = risk.initialMarginRate();
    instrumentId = INSTRUMENT_ID;
    log.info("<<< setUp completed");
  }

  @Test
  public void testTradeFlow_1() {
    log.info(">>> Starting testPositionTradingFlow");
    // 讀取帳號 A 資產 (現貨/逐倉) 作為基準
    AccountBalanceItem initialSpot = AccountClient.getSpotAccount(tokenA);
    BigDecimal baseSpotBalance = initialSpot.balance();
    assertNotNull(baseSpotBalance, "Initial baseSpotBalance  missing");

    ExpectedPosition prevPos;

    // [開倉] A Buy 5 (B Sell 5) @ 100 -> A 預期持多倉 5
    log.info("Scenario [Open Position]: A Buy 5 @ 100");
    prevPos = step1_OpenPosition(baseSpotBalance);
    log.info("Scenario [Open Position]: PASSED");

    // [減倉] A Sell 3 (B Buy 3) @ 101 -> A 預期持多倉 2
    log.info("Scenario [Reduce Position]: A Sell 3 @ 101");
    prevPos = step2_ReducePosition(prevPos, baseSpotBalance);
    log.info("Scenario [Reduce Position]: PASSED");

    // [增倉] A Buy 2 (B Sell 2) @ 102 -> A 預期持多倉 4
    log.info("Scenario [Increase Position]: A Buy 2 @ 102");
    prevPos = step3_IncreasePosition(prevPos, baseSpotBalance);
    log.info("Scenario [Increase Position]: PASSED");

    // [平倉] A Sell 4 (B Buy 4) @ 99 -> A 預期持倉 0
    log.info("Scenario [Close Position]: A Sell 4 @ 99");
    prevPos = step4_ClosePosition(prevPos, baseSpotBalance);
    log.info("Scenario [Close Position]: PASSED");

    // [再次開倉] A Buy 5 (B Sell 5) @ 98 -> A 預期持多倉 5
    log.info("Scenario [Reopen Position]: A Buy 5 @ 98");
    prevPos = step5_ReopenPosition(baseSpotBalance);
    log.info("Scenario [Reopen Position]: PASSED");

    /**
     * 以下 flip 流程，需移除此驗證邏輯
     * open/vincentf13/exchange/position/domain/service/PositionDomainService.java:92
     *
     * <p>因為 flip 補償流程，要補償的場景是: 多筆訂單，在撮合長時間後才陸續成交 在結算時造成的 flip 當真的發生時，會自動平倉後反向開倉，再扣除用戶資金。
     * 當用戶資金為負數時，需觸發強平流程。 ( 此處未已強平流程實現，僅簡單再用戶餘額為負數時禁止開倉)
     *
     * <p>因此一般正常的下單，會有這個驗證邏輯，擋下flip。 移除這個驗證，才方便進行 flip 場景測試。
     */

    // [Flip 反手] A Sell 10 (B Buy 5 + C Buy 5) @ 100 -> A 預期持空倉 5
    log.info("Scenario [Flip Position]: A Sell 10 @ 100");
    prevPos = step6_FlipPosition(prevPos, baseSpotBalance);
    log.info("Scenario [Flip Position]: PASSED");

    // [Flip 搶奪預留倉位] A  (Buy 3 @100 , Buy 10 @101) ，B 先成交 10，再成交 3 -> A 預期持多倉 8
    //                          ^^ 這筆預留 3 ，將被後面的成交 10 搶去平倉，並且反向開倉。  之後成交這筆預留 3 時 ， 自動平倉轉開倉。
    log.info("Scenario [Flip Stealing Reserved Position]: A Buy 3 & 10 @ 100");
    prevPos = step7_ConcurrentFlipPosition(prevPos, baseSpotBalance);
    log.info("Scenario [Flip Stealing Reserved Position]: PASSED");

    // [Flip 搶奪預留倉位] A (Sell 5 @100 , Sell 6 @101 )， B 先成交 6，再成交 5 -> A 預期空倉 3
    //                          ^^ 這筆預留 5，將被後面的成交 6 搶去平倉。 倉位會剩下 空倉2，之後成交預留 5 時，將先平倉 2 再 開倉3。
    log.info("Scenario [Flip Stealing Reserved Position 2]: A Sell 5 @ 100 & 6 @ 101");
    prevPos = step8_ConcurrentFlipPosition2(prevPos, baseSpotBalance);
    log.info("Scenario [Flip Stealing Reserved Position 2]: PASSED");

    log.info("<<< testPositionTradingFlow completed successfully");
  }

  private ExpectedPosition step1_OpenPosition(BigDecimal baseSpotBalance) {
    BigDecimal price = new BigDecimal("100");
    BigDecimal qty = new BigDecimal("5000");
    submitOrder(tokenA, OrderSide.BUY, price, qty, TradeRole.MAKER);
    submitOrder(tokenB, OrderSide.SELL, price, qty, TradeRole.TAKER);

    ExpectedPosition pos =
        new ExpectedPosition(
            PositionStatus.ACTIVE,
            PositionSide.LONG,
            new BigDecimal("5000"),
            new BigDecimal("100"),
            price,
            new BigDecimal("-0.1"), // Fee: 5 * 100 * 0.0002 = 0.1, PnL - Fee = 0 - 0.1 = -0.1
            new BigDecimal("0.1"),
            BigDecimal.ZERO);
    verifyPosition(tokenA, pos);

    BigDecimal expMargin = pos.entryPrice.multiply(pos.qty).multiply(contractSize).multiply(imr);
    BigDecimal expSpot = baseSpotBalance.subtract(expMargin).add(pos.cumRealizedPnl);
    verifyAccount(
        tokenA,
        new ExpectedAccount(
            expSpot, expSpot, BigDecimal.ZERO, expMargin, expMargin, BigDecimal.ZERO));
    return pos;
  }

  private ExpectedPosition step2_ReducePosition(
      ExpectedPosition prevPos, BigDecimal baseSpotBalance) {
    BigDecimal price = new BigDecimal("101");
    BigDecimal qty = new BigDecimal("3000");
    submitOrder(tokenA, OrderSide.SELL, price, qty, TradeRole.MAKER);
    submitOrder(tokenB, OrderSide.BUY, price, qty, TradeRole.TAKER);

    ExpectedPosition pos =
        new ExpectedPosition(
            PositionStatus.ACTIVE,
            PositionSide.LONG,
            new BigDecimal("2000"),
            new BigDecimal("100"), // Entry = (100 * 2) / 2 = 100 (reduce only)
            price, // Mark
            new BigDecimal(
                "2.8394"), // PnL: (101 - 100) * 3 = 3, Fee: 3 * 101 * 0.0002 = 0.0606, PnL - Fee =
            // 2.9394, Cum = -0.1 + 2.9394 = 2.8394
            new BigDecimal("0.1606"), // Fee: 0.1 + 0.0606 = 0.1606
            BigDecimal.ZERO);
    verifyPosition(tokenA, pos);

    BigDecimal expMargin = pos.entryPrice.multiply(pos.qty).multiply(contractSize).multiply(imr);
    BigDecimal expSpot = baseSpotBalance.subtract(expMargin).add(pos.cumRealizedPnl);
    verifyAccount(
        tokenA,
        new ExpectedAccount(
            expSpot, expSpot, BigDecimal.ZERO, expMargin, expMargin, BigDecimal.ZERO));
    return pos;
  }

  private ExpectedPosition step3_IncreasePosition(
      ExpectedPosition prevPos, BigDecimal baseSpotBalance) {
    BigDecimal price = new BigDecimal("102");
    BigDecimal qty = new BigDecimal("2000");
    submitOrder(tokenA, OrderSide.BUY, price, qty, TradeRole.MAKER);
    submitOrder(tokenB, OrderSide.SELL, price, qty, TradeRole.TAKER);

    ExpectedPosition pos =
        new ExpectedPosition(
            PositionStatus.ACTIVE,
            PositionSide.LONG,
            new BigDecimal("4000"),
            new BigDecimal("101"), // Entry avg = (100 * 2 + 102 * 2) / 4 = 101
            price, // Mark
            new BigDecimal(
                "2.7986"), // PnL: 3 (No change on increase), Fee: 2 * 102 * 0.0002 = 0.0408, PnL -
            // Fee = -0.0408, Cum = 2.8394 - 0.0408 = 2.7986
            new BigDecimal("0.2014"), // Fee: 0.1606 + 0.0408 = 0.2014
            BigDecimal.ZERO);
    verifyPosition(tokenA, pos);

    BigDecimal expMargin = pos.entryPrice.multiply(pos.qty).multiply(contractSize).multiply(imr);
    BigDecimal expSpot = baseSpotBalance.subtract(expMargin).add(pos.cumRealizedPnl);
    verifyAccount(
        tokenA,
        new ExpectedAccount(
            expSpot, expSpot, BigDecimal.ZERO, expMargin, expMargin, BigDecimal.ZERO));
    return pos;
  }

  private ExpectedPosition step4_ClosePosition(
      ExpectedPosition prevPos, BigDecimal baseSpotBalance) {
    BigDecimal price = new BigDecimal("99");
    BigDecimal qty = new BigDecimal("4000");
    submitOrder(tokenA, OrderSide.SELL, price, qty, TradeRole.MAKER);
    submitOrder(tokenB, OrderSide.BUY, price, qty, TradeRole.TAKER);

    ExpectedPosition pos =
        new ExpectedPosition(
            PositionStatus.CLOSED,
            PositionSide.LONG,
            BigDecimal.ZERO,
            new BigDecimal("101"), // Entry avg = 101
            price, // Mark
            new BigDecimal(
                "-5.2806"), // PnL: (99 - 101) * 4 = -8, Fee: 4 * 99 * 0.0002 = 0.0792, PnL - Fee =
            // -8.0792, Cum = 2.7986 - 8.0792 = -5.2806
            new BigDecimal("0.2806"), // Fee: 0.2014 + 0.0792 = 0.2806
            BigDecimal.ZERO);
    verifyPosition(tokenA, pos);

    BigDecimal expMargin = BigDecimal.ZERO;
    BigDecimal expSpot = baseSpotBalance.subtract(expMargin).add(pos.cumRealizedPnl);
    verifyAccount(
        tokenA,
        new ExpectedAccount(
            expSpot, expSpot, BigDecimal.ZERO, expMargin, expMargin, BigDecimal.ZERO));
    return pos;
  }

  private ExpectedPosition step5_ReopenPosition(BigDecimal baseSpotBalance) {
    BigDecimal price = new BigDecimal("98");
    BigDecimal qty = new BigDecimal("5000");
    submitOrder(tokenA, OrderSide.BUY, price, qty, TradeRole.MAKER);
    submitOrder(tokenB, OrderSide.SELL, price, qty, TradeRole.TAKER);

    ExpectedPosition pos =
        new ExpectedPosition(
            PositionStatus.ACTIVE,
            PositionSide.LONG,
            new BigDecimal("5000"),
            new BigDecimal("98"), // Entry avg = 98
            price, // Mark
            new BigDecimal(
                "-0.098"), // Fee: 5 * 98 * 0.0002 = 0.098, PnL - Fee = -0.098 (Reset after close)
            new BigDecimal("0.098"),
            BigDecimal.ZERO);
    verifyPosition(tokenA, pos);

    BigDecimal expMargin = pos.entryPrice.multiply(pos.qty).multiply(contractSize).multiply(imr);
    BigDecimal expSpot = new BigDecimal("9847.6214"); // repoen 這裡要加上 所有 closed 倉位的已實現盈虧 不能用算的
    verifyAccount(
        tokenA,
        new ExpectedAccount(
            expSpot, expSpot, BigDecimal.ZERO, expMargin, expMargin, BigDecimal.ZERO));
    return pos;
  }

  private ExpectedPosition step6_FlipPosition(
      ExpectedPosition prevPos, BigDecimal baseSpotBalance) {
    BigDecimal price = new BigDecimal("100");
    BigDecimal qty = new BigDecimal("10000");
    submitOrder(tokenA, OrderSide.SELL, price, qty, TradeRole.MAKER);
    submitOrder(tokenC, OrderSide.BUY, price, new BigDecimal("10000"), TradeRole.TAKER);

    ExpectedPosition pos =
        new ExpectedPosition(
            PositionStatus.ACTIVE,
            PositionSide.SHORT,
            new BigDecimal("5000"),
            new BigDecimal("100"), // Entry avg = 100
            price, // Mark
            new BigDecimal(
                "-0.1"), // Fee: 5 * 100 * 0.0002 = 0.1, PnL - Fee = -0.1 (Reset after flip)
            new BigDecimal("0.1"),
            BigDecimal.ZERO);
    verifyPosition(tokenA, pos);

    BigDecimal expMargin = pos.entryPrice.multiply(pos.qty).multiply(contractSize).multiply(imr);
    BigDecimal expSpot = new BigDecimal("9854.4214"); // repoen 這裡要加上 所有 closed 倉位的已實現盈虧 不能用算的
    verifyAccount(
        tokenA,
        new ExpectedAccount(
            expSpot, expSpot, BigDecimal.ZERO, expMargin, expMargin, BigDecimal.ZERO));
    return pos;
  }

  private ExpectedPosition step7_ConcurrentFlipPosition(
      ExpectedPosition prevPos, BigDecimal baseSpotBalance) {
    BigDecimal price = new BigDecimal("100");

    submitOrder(tokenA, OrderSide.BUY, price, new BigDecimal("3000"), TradeRole.MAKER);
    // 價格出101，讓以下二筆先成交
    submitOrder(
        tokenA, OrderSide.BUY, new BigDecimal("101"), new BigDecimal("10000"), TradeRole.MAKER);
    // 這筆成交 A 會搶奪預留的3個倉位 --> 預期 flip Long 倉 5
    submitOrder(
        tokenB, OrderSide.SELL, new BigDecimal("101"), new BigDecimal("10000"), TradeRole.TAKER);

    // 這筆成交 A 會平倉轉開倉   --> 預期 Long 倉 8
    submitOrder(tokenB, OrderSide.SELL, price, new BigDecimal("3000"), TradeRole.TAKER);

    ExpectedPosition pos =
        new ExpectedPosition(
            PositionStatus.ACTIVE,
            PositionSide.LONG,
            new BigDecimal("8000"),
            new BigDecimal("100.625"),
            price, // Mark
            new BigDecimal("-0.161"),
            new BigDecimal("0.161"),
            BigDecimal.ZERO);
    verifyPosition(tokenA, pos);

    BigDecimal expMargin = pos.entryPrice.multiply(pos.qty).multiply(contractSize).multiply(imr);
    BigDecimal expSpot = new BigDecimal("9757.6594");
    verifyAccount(
        tokenA,
        new ExpectedAccount(
            expSpot, expSpot, BigDecimal.ZERO, expMargin, expMargin, BigDecimal.ZERO));
    return pos;
  }

  private ExpectedPosition step8_ConcurrentFlipPosition2(
      ExpectedPosition prevPos, BigDecimal baseSpotBalance) {
    BigDecimal price1 = new BigDecimal("100");
    BigDecimal price2 = new BigDecimal("101");

    submitOrder(tokenA, OrderSide.SELL, price1, new BigDecimal("5000"), TradeRole.MAKER);
    submitOrder(tokenA, OrderSide.SELL, price2, new BigDecimal("6000"), TradeRole.MAKER);

    submitOrder(tokenB, OrderSide.BUY, price2, new BigDecimal("6000"), TradeRole.TAKER);
    submitOrder(tokenB, OrderSide.BUY, price1, new BigDecimal("5000"), TradeRole.TAKER);

    ExpectedPosition pos =
        new ExpectedPosition(
            PositionStatus.ACTIVE,
            PositionSide.SHORT,
            new BigDecimal("3000"),
            new BigDecimal("100"),
            price1, // Mark
            new BigDecimal(
                "-0.06"), // Fee for Open Short 3: 3 * 100 * 0.0002 = 0.06. Net PnL = -Fee
            new BigDecimal("0.06"),
            BigDecimal.ZERO);
    verifyPosition(tokenA, pos);

    BigDecimal newMargin = pos.entryPrice.multiply(pos.qty).multiply(contractSize).multiply(imr);
    BigDecimal expSpot = new BigDecimal("9757.6594");

    verifyAccount(
        tokenA,
        new ExpectedAccount(
            expSpot, expSpot, BigDecimal.ZERO, newMargin, newMargin, BigDecimal.ZERO));
    return pos;
  }

  private void verifyPosition(String token, ExpectedPosition exp) {
    PositionResponse pos = PositionClient.findPosition(token, instrumentId);
    assertNotNull(pos, "Position not found");

    // Status (狀態)
    assertEquals(exp.status, pos.status(), "Status mismatch");

    // Side (方向)
    assertEquals(exp.side, pos.side(), "Side mismatch");

    // Leverage (槓桿)
    assertEquals(this.leverage, pos.leverage(), "Leverage mismatch");

    // Margin (保證金)
    // 計算公式: Price * Quantity * ContractValue * InitialMarginRate
    BigDecimal expectedMargin =
        exp.entryPrice.multiply(exp.qty).multiply(contractSize).multiply(imr);
    assertNear(expectedMargin, pos.margin(), "Margin mismatch");

    // Entry Price (入場價)
    assertNear(exp.entryPrice, pos.entryPrice(), "Entry Price mismatch");

    // Quantity (持倉量)
    assertNear(exp.qty, pos.quantity(), "Quantity mismatch");

    // Closing Reserved Quantity (平倉凍結量)
    assertNear(
        exp.closeReserved, pos.closingReservedQuantity(), "Closing Reserved Quantity mismatch");

    // Mark Price (標記價格)
    assertNear(exp.markPrice, pos.markPrice(), "Mark Price mismatch");

    // Margin Ratio (保證金率)
    // Ratio = Equity / Notional = (Margin + Upnl) / (Mark * Qty * Size)
    BigDecimal priceDiff =
        (exp.side == PositionSide.LONG)
            ? exp.markPrice.subtract(exp.entryPrice)
            : exp.entryPrice.subtract(exp.markPrice);
    BigDecimal expectedUpnl = priceDiff.multiply(exp.qty).multiply(contractSize);

    BigDecimal expectedNotional = exp.markPrice.multiply(exp.qty).multiply(contractSize);
    BigDecimal expectedEquity = expectedMargin.add(expectedUpnl);

    if (expectedNotional.compareTo(BigDecimal.ZERO) > 0) {
      BigDecimal expectedRatio = expectedEquity.divide(expectedNotional, 10, RoundingMode.HALF_UP);
      assertNear(
          expectedRatio, pos.marginRatio(), new BigDecimal("0.001"), "Margin Ratio mismatch");
    }

    // Unrealized Pnl (未實現損益)
    assertNear(
        expectedUpnl, pos.unrealizedPnl(), new BigDecimal("0.01"), "Unrealized PnL mismatch");

    // Cum Realized Pnl (累計已實現損益)
    assertNear(
        exp.cumRealizedPnl,
        pos.cumRealizedPnl(),
        new BigDecimal("0.01"),
        "Cum Realized PnL mismatch");

    // Cum Fee (累計手續費)
    assertNear(exp.cumFee, pos.cumFee(), "Cum Fee mismatch");

    // Cum Funding Fee (累計資金費)
    assertNear(BigDecimal.ZERO, pos.cumFundingFee(), "Cum Funding Fee mismatch");

    // Liquidation Price (強平價格)
    if (exp.qty.compareTo(BigDecimal.ZERO) > 0) {
      BigDecimal marginPerUnit =
          expectedMargin.divide(exp.qty.multiply(contractSize), 10, RoundingMode.HALF_UP);
      BigDecimal calcLiqPrice =
          (exp.side == PositionSide.LONG)
              ? (exp.entryPrice.subtract(marginPerUnit))
                  .divide(BigDecimal.ONE.subtract(mmr), 10, RoundingMode.HALF_UP)
              : (exp.entryPrice.add(marginPerUnit))
                  .divide(BigDecimal.ONE.add(mmr), 10, RoundingMode.HALF_UP);

      assertNotNull(pos.liquidationPrice(), "Liquidation Price should not be null");
      assertNear(
          calcLiqPrice,
          pos.liquidationPrice(),
          new BigDecimal("0.1"),
          "Liquidation Price mismatch");
    }
  }

  /** 驗證帳戶餘額 (對應 .http 的 verifyAccountScript) */
  private void verifyAccount(String token, ExpectedAccount exp) {
    AccountBalanceSheetResponse data = AccountClient.getBalanceSheet(token);

    assertNotNull(data, "Account balance sheet missing");

    AccountBalanceItem spot = getAccount(data, UserAccountCode.SPOT);
    AccountBalanceItem margin = getAccount(data, UserAccountCode.MARGIN);
    assertNotNull(spot, "Spot account not found");
    assertNotNull(margin, "Margin account not found");

    assertNear(exp.spotBalance, spot.balance(), "Spot balance");
    assertNear(exp.spotAvailable, spot.available(), "Spot available");
    assertNear(exp.spotReserved, spot.reserved(), "Spot reserved");

    assertNear(exp.marginBalance, margin.balance(), "Margin balance");
    assertNear(exp.marginAvailable, margin.available(), "Margin available");
    assertNear(BigDecimal.ZERO, margin.reserved(), "Margin reserved");
  }

  private void submitOrder(
      String token, OrderSide side, BigDecimal price, BigDecimal quantity, TradeRole role) {
    OrderClient.placeOrder(
        token, instrumentId, side, price.doubleValue(), quantity.intValueExact());
    pause(role == TradeRole.MAKER ? MAKER_DELAY_MS : TAKER_DELAY_MS);
  }

  private AccountBalanceItem getAccount(AccountBalanceSheetResponse sheet, UserAccountCode code) {
    if (sheet.assets() == null) return null;
    return sheet.assets().stream()
        .filter(a -> code.equals(a.accountCode()) && AssetSymbol.USDT.equals(a.asset()))
        .filter(
            a ->
                code == UserAccountCode.SPOT
                    || (a.instrumentId() != null && a.instrumentId().intValue() == INSTRUMENT_ID))
        .findFirst()
        .orElse(null);
  }

  private static void pause(long delayMs) {
    try {
      Thread.sleep(delayMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void assertNear(BigDecimal expected, BigDecimal actual, String label) {
    assertNear(expected, actual, new BigDecimal("0.0001"), label);
  }

  private void assertNear(
      BigDecimal expected, BigDecimal actual, BigDecimal tolerance, String label) {
    BigDecimal diff = expected.subtract(actual).abs();
    assertTrue(
        diff.compareTo(tolerance) <= 0,
        String.format("%s mismatch. Exp: %s, Got: %s", label, expected, actual));
  }

  private enum TradeRole {
    TAKER,
    MAKER
  }

  private record ExpectedPosition(
      PositionStatus status,
      PositionSide side,
      BigDecimal qty,
      BigDecimal entryPrice,
      BigDecimal markPrice,
      BigDecimal cumRealizedPnl,
      BigDecimal cumFee,
      BigDecimal closeReserved) {}

  private record ExpectedAccount(
      BigDecimal spotBalance,
      BigDecimal spotAvailable,
      BigDecimal spotReserved,
      BigDecimal marginBalance,
      BigDecimal marginAvailable,
      BigDecimal marginReserved) {}
}
