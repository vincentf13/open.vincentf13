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
  void testScenario1_OpenPosition_5Contracts() {
    // 讀取帳號 A 資產 (現貨/逐倉) 作為基準
    AccountBalanceItem initialSpot = AccountClient.getSpotAccount(tokenA);
    BigDecimal baseSpotBalance = initialSpot.balance();

    // [開倉] A Buy 5 (B Sell 5) @ 100 -> A 預期持多倉 5
    submitOrder(tokenA, OrderSide.BUY, BigDecimal.valueOf(100), BigDecimal.valueOf(5000), TradeRole.MAKER);

    // B 成交 A 的 5 張
    submitOrder(tokenB, OrderSide.SELL, BigDecimal.valueOf(100), BigDecimal.valueOf(5000), TradeRole.TAKER);

    // ### 驗證 A 持倉: Long 5, Price 100
    verifyPosition(tokenA, new ExpectedPosition(
        PositionStatus.ACTIVE,
        PositionSide.LONG,
        BigDecimal.valueOf(5000),
        BigDecimal.valueOf(100),
        BigDecimal.valueOf(100),
        BigDecimal.valueOf(-0.1), // Fee (this step): 5 * 100 * 0.0002 = 0.1, PnL - Fee = 0 - 0.1 = -0.1
        BigDecimal.valueOf(0.1),
        BigDecimal.ZERO
    ));

    // 驗證 A 資產: Spot/Margin
 
    verifyAccount(tokenA, baseSpotBalance, BigDecimal.valueOf(100), BigDecimal.valueOf(5000), BigDecimal.valueOf(-0.1));
  }


  
  private void verifyPosition(String token, ExpectedPosition exp) {
    PositionResponse pos = PositionClient.findPosition(token, instrumentId);
    assertNotNull(pos, "Position not found");

    // 1. Status (狀態)
    assertEquals(exp.status, pos.status(), "Status mismatch");

    // 2. Side (方向)
    assertEquals(exp.side, pos.side(), "Side mismatch");

    // 3. Leverage (槓桿)
    assertEquals(this.leverage, pos.leverage(), "Leverage mismatch");

    // 4. Margin (保證金)
    // 計算公式: Price * Quantity * ContractValue * InitialMarginRate
    BigDecimal expectedMargin = exp.entryPrice.multiply(exp.qty).multiply(contractSize).multiply(imr);
    assertNear(expectedMargin, pos.margin(), "Margin mismatch");

    // 5. Entry Price (入場價)
    assertNear(exp.entryPrice, pos.entryPrice(), "Entry Price mismatch");

    // 6. Quantity (持倉量)
    assertNear(exp.qty, pos.quantity(), "Quantity mismatch");

    // 7. Closing Reserved Quantity (平倉凍結量)
    assertNear(exp.closeReserved, pos.closingReservedQuantity(), "Closing Reserved Quantity mismatch");

    // 8. Mark Price (標記價格)
    assertNear(exp.markPrice, pos.markPrice(), "Mark Price mismatch");

    // 9. Margin Ratio (保證金率)
    // Ratio = Equity / Notional = (Margin + Upnl) / (Mark * Qty * Size)
    BigDecimal notional = pos.markPrice().multiply(pos.quantity()).multiply(contractSize);
    BigDecimal equity = pos.margin().add(pos.unrealizedPnl());
    if (notional.compareTo(BigDecimal.ZERO) > 0) {
      BigDecimal expectedRatio = equity.divide(notional, 10, RoundingMode.HALF_UP);
      assertNear(expectedRatio, pos.marginRatio(), BigDecimal.valueOf(0.001), "Margin Ratio mismatch");
    }

    // 10. Unrealized Pnl (未實現損益)
    BigDecimal priceDiff = (pos.side() == PositionSide.LONG) 
        ? pos.markPrice().subtract(pos.entryPrice()) 
        : pos.entryPrice().subtract(pos.markPrice());
    BigDecimal expectedUpnl = priceDiff.multiply(pos.quantity()).multiply(contractSize);
    assertNear(expectedUpnl, pos.unrealizedPnl(), BigDecimal.valueOf(0.01), "Upnl mismatch");

    // 11. Cum Realized Pnl (累計已實現損益)
    assertNear(exp.cumRealizedPnl, pos.cumRealizedPnl(), BigDecimal.valueOf(0.01), "Cum Realized Pnl mismatch");

    // 12. Cum Fee (累計手續費)
    assertNear(exp.cumFee, pos.cumFee(), "Cum Fee mismatch");

    // 13. Cum Funding Fee (累計資金費)
    assertNear(exp.cumFundingFee, pos.cumFundingFee(), "Cum Funding Fee mismatch");

    // 14. Liquidation Price (強平價格)
    if (pos.quantity().compareTo(BigDecimal.ZERO) > 0) {
        BigDecimal marginPerUnit = pos.margin().divide(pos.quantity().multiply(contractSize), 10, RoundingMode.HALF_UP);
        BigDecimal calcLiqPrice = (pos.side() == PositionSide.LONG)
            ? (pos.entryPrice().subtract(marginPerUnit)).divide(BigDecimal.ONE.subtract(mmr), 10, RoundingMode.HALF_UP)
            : (pos.entryPrice().add(marginPerUnit)).divide(BigDecimal.ONE.add(mmr), 10, RoundingMode.HALF_UP);
        
        assertNotNull(pos.liquidationPrice(), "Liquidation Price should not be null");
        assertNear(calcLiqPrice, pos.liquidationPrice(), BigDecimal.valueOf(0.1), "Liquidation Price mismatch");
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

  // ==================================================================================
  // Helpers
  // ==================================================================================

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
    assertNear(expected, actual, BigDecimal.valueOf(0.0001), label);
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
      BigDecimal cumFundingFee,
      BigDecimal closeReserved
  ) {
    ExpectedPosition(PositionStatus status, PositionSide side, BigDecimal qty, BigDecimal entryPrice, BigDecimal markPrice, BigDecimal cumRealizedPnl, BigDecimal cumFee, BigDecimal cumFundingFee) {
        this(status, side, qty, entryPrice, markPrice, cumRealizedPnl, cumFee, cumFundingFee, BigDecimal.ZERO);
    }
  }
}