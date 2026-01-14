package open.vincentf13.exchange.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import open.vincentf13.exchange.test.client.AccountClient;
import open.vincentf13.exchange.test.client.AdminClient;
import open.vincentf13.exchange.test.client.AuthClient;
import open.vincentf13.exchange.test.client.BaseClient;
import open.vincentf13.exchange.test.client.OrderClient;
import open.vincentf13.exchange.test.client.PositionClient;
import open.vincentf13.exchange.test.client.RiskClient;
import open.vincentf13.exchange.test.client.SystemClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PositionApiRestAssuredTest {
  private static final String GATEWAY_HOST = "http://localhost:12345";
  private static final int INSTRUMENT_ID = 10001;
  private static final String PASSWORD = "12345678";
  private static final String USER_A = "c.p.kevinf13@gmail.com";
  private static final String USER_B = "c.p.kevinf13-2@gmail.com";
  private static final String USER_C = "c.p.kevinf13-3@gmail.com";
  private static final long MAKER_DELAY_MS = 2000;
  private static final long TAKER_DELAY_MS = 5000;

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
  void setUp() {
    BaseClient.setHost(GATEWAY_HOST);
    SystemClient.resetData();

    tokenA = AuthClient.login(USER_A, PASSWORD);
    tokenB = AuthClient.login(USER_B, PASSWORD);
    tokenC = AuthClient.login(USER_C, PASSWORD);

    AccountClient.deposit(tokenA, 10000);
    AccountClient.deposit(tokenB, 10000);
    AccountClient.deposit(tokenC, 10000);

    InstrumentDetailResponse instrument = AdminClient.getInstrument(INSTRUMENT_ID);
    RiskLimitResponse risk = RiskClient.getRiskLimit(INSTRUMENT_ID);

    contractSize = instrument.contractSize();
    makerFeeRate = instrument.makerFeeRate();
    takerFeeRate = instrument.takerFeeRate();
    leverage = instrument.defaultLeverage();
    mmr = risk.maintenanceMarginRate();
    imr = risk.initialMarginRate();
    instrumentId = INSTRUMENT_ID;
  }

  @Test
  void testPositionTradingFlow() {
    // 讀取帳號 A 資產 (現貨/逐倉) 作為基準
    AccountBalanceItem initialSpot = AccountClient.getSpotAccount(tokenA);
    BigDecimal baseSpotBalance = initialSpot.balance();
    assertNotNull(baseSpotBalance, "Initial baseSpotBalance  missing");

    // [開倉] A Buy 5 (B Sell 5) @ 100 -> A 預期持多倉 5
    submitOrder(tokenA, OrderSide.BUY, new BigDecimal("100"), new BigDecimal("5000"), TradeRole.MAKER);

    // B 成交 A 的 5 張
    submitOrder(tokenB, OrderSide.SELL, new BigDecimal("100"), new BigDecimal("5000"), TradeRole.TAKER);

    // 驗證 A 持倉: Long 5, Price 100
    verifyPosition(tokenA, new ExpectedPosition(
        PositionStatus.ACTIVE,
        PositionSide.LONG,
        new BigDecimal("5000"),
        new BigDecimal("100"),
        new BigDecimal("100"),
        new BigDecimal("-0.1"), // Fee (this step): 5 * 100 * 0.0002 = 0.1, PnL - Fee = 0 - 0.1 = -0.1
        new BigDecimal("0.1"),
        BigDecimal.ZERO
    ));

    // 驗證 A 資產: Spot/Margin
    verifyAccount(tokenA, baseSpotBalance, new BigDecimal("100"), new BigDecimal("5000"), new BigDecimal("-0.1"));

    // [減倉] A Sell 3 (B Buy 3) @ 101 -> A 預期持多倉 2
    submitOrder(tokenA, OrderSide.SELL, new BigDecimal("101"), new BigDecimal("3000"), TradeRole.MAKER);

    // B 成交 A 的 3 張
    submitOrder(tokenB, OrderSide.BUY, new BigDecimal("101"), new BigDecimal("3000"), TradeRole.TAKER);

    // 驗證 A 持倉: Long 2
    verifyPosition(tokenA, new ExpectedPosition(
        PositionStatus.ACTIVE,
        PositionSide.LONG,
        new BigDecimal("2000"),
        new BigDecimal("100"), // Entry = (100 * 2) / 2 = 100 (reduce only)
        new BigDecimal("101"), // Mark
        new BigDecimal("2.8394"), // PnL: (101 - 100) * 3 = 3, Fee: 3 * 101 * 0.0002 = 0.0606, PnL - Fee = 2.9394, Cum = -0.1 + 2.9394 = 2.8394
        new BigDecimal("0.1606"), // Fee: 0.1 + 0.0606 = 0.1606
        BigDecimal.ZERO
    ));

    // 驗證 A 資產: Spot/Margin
    verifyAccount(tokenA, baseSpotBalance, new BigDecimal("100"), new BigDecimal("2000"), new BigDecimal("2.8394"));

    // [增倉] A Buy 2 (B Sell 2) @ 102 -> A 預期持多倉 4
    submitOrder(tokenA, OrderSide.BUY, new BigDecimal("102"), new BigDecimal("2000"), TradeRole.MAKER);

    // B 成交 A 的 2 張
    submitOrder(tokenB, OrderSide.SELL, new BigDecimal("102"), new BigDecimal("2000"), TradeRole.TAKER);

    // 驗證 A 持倉: Long 4
    verifyPosition(tokenA, new ExpectedPosition(
        PositionStatus.ACTIVE,
        PositionSide.LONG,
        new BigDecimal("4000"),
        new BigDecimal("101"), // Entry avg = (100 * 2 + 102 * 2) / 4 = 101
        new BigDecimal("102"), // Mark
        new BigDecimal("2.7986"), // PnL: 3 (No change on increase), Fee: 2 * 102 * 0.0002 = 0.0408, PnL - Fee = -0.0408, Cum = 2.8394 - 0.0408 = 2.7986
        new BigDecimal("0.2014"), // Fee: 0.1606 + 0.0408 = 0.2014
        BigDecimal.ZERO
    ));

    // 驗證 A 資產: Spot/Margin
    verifyAccount(tokenA, baseSpotBalance, new BigDecimal("101"), new BigDecimal("4000"), new BigDecimal("2.7986"));

    // [平倉] A Sell 4 (B Buy 4) @ 99 -> A 預期持倉 0 (因為之前是多倉 4)
    submitOrder(tokenA, OrderSide.SELL, new BigDecimal("99"), new BigDecimal("4000"), TradeRole.MAKER);

    // B 成交 A 的 4 張
    submitOrder(tokenB, OrderSide.BUY, new BigDecimal("99"), new BigDecimal("4000"), TradeRole.TAKER);

    // 驗證 A 持倉: Closed
    verifyPosition(tokenA, new ExpectedPosition(
        PositionStatus.CLOSED,
        PositionSide.LONG,
        BigDecimal.ZERO,
        new BigDecimal("101"), // Entry avg = 101
        new BigDecimal("99"), // Mark
        new BigDecimal("-5.2806"), // PnL: (99 - 101) * 4 = -8, Fee: 4 * 99 * 0.0002 = 0.0792, PnL - Fee = -8.0792, Cum = 2.7986 - 8.0792 = -5.2806
        new BigDecimal("0.2806"), // Fee: 0.2014 + 0.0792 = 0.2806
        BigDecimal.ZERO
    ));

    // 驗證 A 資產: Spot/Margin
    verifyAccount(tokenA, baseSpotBalance, new BigDecimal("101"), BigDecimal.ZERO, new BigDecimal("-5.2806"));

    // [再次開倉] A Buy 5 (B Sell 5) @ 98 -> A 預期持多倉 5
    submitOrder(tokenA, OrderSide.BUY, new BigDecimal("98"), new BigDecimal("5000"), TradeRole.MAKER);

    // B 成交 A 的 5 張
    submitOrder(tokenB, OrderSide.SELL, new BigDecimal("98"), new BigDecimal("5000"), TradeRole.TAKER);

    // 驗證 A 持倉: Long 5
    verifyPosition(tokenA, new ExpectedPosition(
        PositionStatus.ACTIVE,
        PositionSide.LONG,
        new BigDecimal("5000"),
        new BigDecimal("98"), // Entry avg = 98
        new BigDecimal("98"), // Mark
        new BigDecimal("-0.098"), // Fee: 5 * 98 * 0.0002 = 0.098, PnL - Fee = -0.098 (Reset after close)
        new BigDecimal("0.098"),
        BigDecimal.ZERO
    ));

    // [Flip 反手] A Sell 10 (B Buy 5 + C Buy 5) @ 100 -> A 預期持空倉 5 (原多 5 - 賣 10)
    // A 原本持多倉 5，賣 10 變空 5
    submitOrder(tokenA, OrderSide.SELL, new BigDecimal("100"), new BigDecimal("10000"), TradeRole.MAKER);

    // C 成交 A 的 5 張
    submitOrder(tokenC, OrderSide.BUY, new BigDecimal("100"), new BigDecimal("5000"), TradeRole.TAKER);

    // B 成交 A 的 5 張
    submitOrder(tokenB, OrderSide.BUY, new BigDecimal("100"), new BigDecimal("5000"), TradeRole.TAKER);

    // 驗證 A 持倉: Short 5
    verifyPosition(tokenA, new ExpectedPosition(
        PositionStatus.ACTIVE,
        PositionSide.SHORT,
        new BigDecimal("5000"),
        new BigDecimal("100"), // Entry avg = 100
        new BigDecimal("100"), // Mark
        new BigDecimal("-0.1"), // Fee: 5 * 100 * 0.0002 = 0.1, PnL - Fee = -0.1 (Reset after flip)
        new BigDecimal("0.1"),
        BigDecimal.ZERO
    ));

    // [Flip 並發測試] A 同時下二單 (Buy 3, Buy 10) @ 100，B 依序成交 -> A 預期持多倉 8
    // A 原本持空倉 5，買 3 變空 2，再買 10 變多 8
    
    // 更新 A 的基準資產作為 Step 11 的基準
    baseSpotBalance = AccountClient.getSpotAccount(tokenA).balance();

    submitOrder(tokenA, OrderSide.BUY, new BigDecimal("100"), new BigDecimal("3000"), TradeRole.MAKER);
    submitOrder(tokenA, OrderSide.BUY, new BigDecimal("100"), new BigDecimal("10000"), TradeRole.MAKER);

    // B 成交 A 的 10 張
    submitOrder(tokenB, OrderSide.SELL, new BigDecimal("100"), new BigDecimal("10000"), TradeRole.TAKER);

    // B 成交 A 的 3 張
    submitOrder(tokenB, OrderSide.SELL, new BigDecimal("100"), new BigDecimal("3000"), TradeRole.TAKER);

    // 驗證 A 持倉: Long 8
    verifyPosition(tokenA, new ExpectedPosition(
        PositionStatus.ACTIVE,
        PositionSide.LONG,
        new BigDecimal("8000"),
        new BigDecimal("100"), // Entry avg = 100
        new BigDecimal("100"), // Mark
        new BigDecimal("-0.26"), // Fee: 13 * 100 * 0.0002 = 0.26, PnL - Fee = -0.26 (Reset after flip)
        new BigDecimal("0.26"),
        BigDecimal.ZERO
    ));

    // 驗證 A 資產: Spot/Margin
    verifyAccount(tokenA, baseSpotBalance, new BigDecimal("100"), new BigDecimal("8000"), new BigDecimal("-0.26"));
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
    BigDecimal expectedMargin = exp.entryPrice.multiply(exp.qty).multiply(contractSize).multiply(imr);
    assertNear(expectedMargin, pos.margin(), "Margin mismatch");

    // Entry Price (入場價)
    assertNear(exp.entryPrice, pos.entryPrice(), "Entry Price mismatch");

    // Quantity (持倉量)
    assertNear(exp.qty, pos.quantity(), "Quantity mismatch");

    // Closing Reserved Quantity (平倉凍結量)
    assertNear(exp.closeReserved, pos.closingReservedQuantity(), "Closing Reserved Quantity mismatch");

    // Mark Price (標記價格)
    assertNear(exp.markPrice, pos.markPrice(), "Mark Price mismatch");

    // Margin Ratio (保證金率)
    // Ratio = Equity / Notional = (Margin + Upnl) / (Mark * Qty * Size)
    BigDecimal notional = pos.markPrice().multiply(pos.quantity()).multiply(contractSize);
    BigDecimal equity = pos.margin().add(pos.unrealizedPnl());
    if (notional.compareTo(BigDecimal.ZERO) > 0) {
      BigDecimal expectedRatio = equity.divide(notional, 10, RoundingMode.HALF_UP);
      assertNear(expectedRatio, pos.marginRatio(), new BigDecimal("0.001"), "Margin Ratio mismatch");
    }

    // Unrealized Pnl (未實現損益)
    BigDecimal priceDiff = (pos.side() == PositionSide.LONG) 
        ? pos.markPrice().subtract(pos.entryPrice()) 
        : pos.entryPrice().subtract(pos.markPrice());
    BigDecimal expectedUpnl = priceDiff.multiply(pos.quantity()).multiply(contractSize);
    assertNear(expectedUpnl, pos.unrealizedPnl(), new BigDecimal("0.01"), "Upnl mismatch");

    // Cum Realized Pnl (累計已實現損益)
    assertNear(exp.cumRealizedPnl, pos.cumRealizedPnl(), new BigDecimal("0.01"), "Cum Realized PnL mismatch");

    // Cum Fee (累計手續費)
    assertNear(exp.cumFee, pos.cumFee(), "Cum Fee mismatch");

    // Cum Funding Fee (累計資金費)
    assertNear(BigDecimal.ZERO, pos.cumFundingFee(), "Cum Funding Fee mismatch");

    // Liquidation Price (強平價格)
    if (pos.quantity().compareTo(BigDecimal.ZERO) > 0) {
        BigDecimal marginPerUnit = pos.margin().divide(pos.quantity().multiply(contractSize), 10, RoundingMode.HALF_UP);
        BigDecimal calcLiqPrice = (pos.side() == PositionSide.LONG)
            ? (pos.entryPrice().subtract(marginPerUnit)).divide(BigDecimal.ONE.subtract(mmr), 10, RoundingMode.HALF_UP)
            : (pos.entryPrice().add(marginPerUnit)).divide(BigDecimal.ONE.add(mmr), 10, RoundingMode.HALF_UP);
        
        assertNotNull(pos.liquidationPrice(), "Liquidation Price should not be null");
        assertNear(calcLiqPrice, pos.liquidationPrice(), new BigDecimal("0.1"), "Liquidation Price mismatch");
    }
  }

  /**
   * 驗證帳戶餘額 (對應 .http 的 verifyAccountScript)
   */
  private void verifyAccount(String token, BigDecimal baseSpotBalance, BigDecimal expEntry, BigDecimal expQty, BigDecimal expCumRealizedPnl) {
    AccountBalanceSheetResponse data = AccountClient.getBalanceSheet(token);
    assertNotNull(data, "Account balance sheet missing");
    
    AccountBalanceItem spot = getAccount(data, UserAccountCode.SPOT);
    AccountBalanceItem margin = getAccount(data, UserAccountCode.MARGIN);
    assertNotNull(spot, "Spot account not found");
    assertNotNull(margin, "Margin account not found");

    BigDecimal expectedMargin = expEntry.multiply(expQty).multiply(contractSize).multiply(imr);
    BigDecimal expectedSpotBalance = baseSpotBalance.subtract(expectedMargin).add(expCumRealizedPnl);

    assertNear(expectedSpotBalance, spot.balance(), "Spot balance");
    assertNear(expectedSpotBalance, spot.available(), "Spot available");
    assertNear(BigDecimal.ZERO, spot.reserved(), "Spot reserved");
    
    assertNear(expectedMargin, margin.balance(), "Margin balance");
    assertNear(expectedMargin, margin.available(), "Margin available");
    assertNear(BigDecimal.ZERO, margin.reserved(), "Margin reserved");
  }


  private void submitOrder(String token, OrderSide side, BigDecimal price, BigDecimal quantity, TradeRole role) {
    OrderClient.placeOrder(token, instrumentId, side, price.doubleValue(), quantity.intValueExact());
    pause(role == TradeRole.MAKER ? MAKER_DELAY_MS : TAKER_DELAY_MS);
  }

  private AccountBalanceItem getAccount(AccountBalanceSheetResponse sheet, UserAccountCode code) {
    if (sheet.assets() == null) return null;
    return sheet.assets().stream()
        .filter(a -> code.equals(a.accountCode()) && AssetSymbol.USDT.equals(a.asset()))
        .filter(a -> code == UserAccountCode.SPOT || (a.instrumentId() != null && a.instrumentId().intValue() == INSTRUMENT_ID))
        .findFirst()
        .orElse(null);
  }

  private static void pause(long delayMs) {
    try { Thread.sleep(delayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
  }

  private void assertNear(BigDecimal expected, BigDecimal actual, String label) {
    assertNear(expected, actual, new BigDecimal("0.0001"), label);
  }

  private void assertNear(BigDecimal expected, BigDecimal actual, BigDecimal tolerance, String label) {
    BigDecimal diff = expected.subtract(actual).abs();
    assertTrue(diff.compareTo(tolerance) <= 0, String.format("%s mismatch. Exp: %s, Got: %s", label, expected, actual));
  }

  private enum TradeRole { TAKER, MAKER }

  private record ExpectedPosition(
      PositionStatus status,
      PositionSide side,
      BigDecimal qty,
      BigDecimal entryPrice,
      BigDecimal markPrice,
      BigDecimal cumRealizedPnl,
      BigDecimal cumFee,
      BigDecimal closeReserved
  ) {}
}