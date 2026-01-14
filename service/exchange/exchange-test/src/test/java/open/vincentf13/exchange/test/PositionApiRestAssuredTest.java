package open.vincentf13.exchange.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
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
import open.vincentf13.sdk.core.validator.OpenValidator;
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

        OpenValidator.assertAllNotNull(
                Map.of(
                        "contractSize", contractSize,
                        "makerFeeRate", makerFeeRate,
                        "takerFeeRate", takerFeeRate,
                        "leverage", leverage,
                        "mmr", mmr,
                        "imr", imr,
                        "instrumentId", instrumentId));
    }

    @Test
    void scenario1_longMakerFlow() {
        
        BigDecimal baseSpotBalance = AccountClient.getSpotAccountBalance(tokenA);
        
        
        PositionSimulator simulator = new PositionSimulator(contractSize);

        OrderSide openSide = OrderSide.BUY;
        BigDecimal openPrice = BigDecimal.valueOf(100);
        BigDecimal openQuantity = BigDecimal.valueOf(5000);
        submitOrder(tokenA, openSide, openPrice, openQuantity, TradeRole.TAKER);
        submitOrder(tokenB, opposite(openSide), openPrice, openQuantity, TradeRole.MAKER);
        
        simulator.apply(openSide, openPrice, openQuantity, takerFeeRate);
        verifyPosition(
                PositionClient.findPosition(tokenA, instrumentId),
                        simulator);
        
        ExpectedAccount openAccount = simulateAccount(simulator);
        
        verifyAccount(AccountClient.getBalanceSheet(tokenA), openAccount);

        OrderSide reduceSide = OrderSide.SELL;
        BigDecimal reducePrice = BigDecimal.valueOf(101);
        BigDecimal reduceQuantity = BigDecimal.valueOf(3000);
        submitOrder(tokenA, reduceSide, reducePrice, reduceQuantity, TradeRole.TAKER);
        submitOrder(tokenB, opposite(reduceSide), reducePrice, reduceQuantity, TradeRole.MAKER);
        PositionResponse position3 = PositionClient.findPosition(tokenA, instrumentId);
        simulator.apply(reduceSide, reducePrice, reduceQuantity, takerFeeRate);
        verifyPosition(position3, simulator);
        ExpectedAccount reduceAccount = simulateAccount(simulator);
        verifyAccount(AccountClient.getBalanceSheet(tokenA), reduceAccount);

        OrderSide increaseSide = OrderSide.BUY;
        BigDecimal increasePrice = BigDecimal.valueOf(102);
        BigDecimal increaseQuantity = BigDecimal.valueOf(2000);
        submitOrder(tokenA, increaseSide, increasePrice, increaseQuantity, TradeRole.TAKER);
        submitOrder(tokenB, opposite(increaseSide), increasePrice, increaseQuantity, TradeRole.MAKER);
        PositionResponse position2 = PositionClient.findPosition(tokenA, instrumentId);
        simulator.apply(increaseSide, increasePrice, increaseQuantity, takerFeeRate);
        verifyPosition(position2, simulator);
        ExpectedAccount increaseAccount = simulateAccount(simulator);
        verifyAccount(AccountClient.getBalanceSheet(tokenA), increaseAccount);

        OrderSide closeSide = OrderSide.SELL;
        BigDecimal closePrice = BigDecimal.valueOf(99);
        BigDecimal closeQuantity = BigDecimal.valueOf(4000);
        submitOrder(tokenA, closeSide, closePrice, closeQuantity, TradeRole.TAKER);
        submitOrder(tokenB, opposite(closeSide), closePrice, closeQuantity, TradeRole.MAKER);
        PositionResponse position1 = PositionClient.findPosition(tokenA, instrumentId);
        simulator.apply(closeSide, closePrice, closeQuantity, takerFeeRate);
        verifyPosition(position1, simulator);
        ExpectedAccount closeAccount = simulateAccount(simulator);
        verifyAccount(AccountClient.getBalanceSheet(tokenA), closeAccount);

        OrderSide reopenSide = OrderSide.BUY;
        BigDecimal reopenPrice = BigDecimal.valueOf(98);
        BigDecimal reopenQuantity = BigDecimal.valueOf(5000);
        submitOrder(tokenA, reopenSide, reopenPrice, reopenQuantity, TradeRole.TAKER);
        submitOrder(tokenB, opposite(reopenSide), reopenPrice, reopenQuantity, TradeRole.MAKER);
        PositionResponse position = PositionClient.findPosition(tokenA, instrumentId);
        simulator.apply(reopenSide, reopenPrice, reopenQuantity, takerFeeRate);
        verifyPosition(position, simulator);
        ExpectedAccount reopenAccount = simulateAccount(simulator);
        verifyAccount(AccountClient.getBalanceSheet(tokenA), reopenAccount);

        StepResult flipResult = executeTradeWithTwoCounterparties(
                simulator, OrderSide.SELL, BigDecimal.valueOf(100), BigDecimal.valueOf(10000));
        verifyPosition(PositionClient.findPosition(tokenA, instrumentId), flipResult.expectedPosition);
        verifyAccount(AccountClient.getBalanceSheet(tokenA), flipResult.expectedAccount);

        StepResult concurrentResult = executeConcurrentFlip(simulator);
        verifyPosition(PositionClient.findPosition(tokenA, instrumentId), concurrentResult.expectedPosition);
        verifyAccount(AccountClient.getBalanceSheet(tokenA), concurrentResult.expectedAccount);
    }

    private StepResult executeTradeWithTwoCounterparties(PositionSimulator simulator,
                                                         OrderSide side,
                                                         BigDecimal price,
                                                         BigDecimal quantity) {
        submitOrder(tokenA, side, price, quantity, TradeRole.TAKER);
        submitOrder(tokenB, opposite(side), price, BigDecimal.valueOf(5000), TradeRole.MAKER);
        submitOrder(tokenC, opposite(side), price, BigDecimal.valueOf(5000), TradeRole.MAKER);
        simulator.apply(side, price, BigDecimal.valueOf(5000), takerFeeRate);
        simulator.apply(side, price, BigDecimal.valueOf(5000), takerFeeRate);
        ExpectedPosition expectedPosition = simulator.snapshot(price);
        ExpectedAccount expectedAccount = simulateAccount(expectedPosition);
        return new StepResult(expectedPosition, expectedAccount);
    }

    private StepResult executeConcurrentFlip(PositionSimulator simulator) {
        OrderSide firstSide = OrderSide.BUY;
        BigDecimal firstPrice = BigDecimal.valueOf(100);
        BigDecimal firstQuantity = BigDecimal.valueOf(3000);
        OrderSide secondSide = OrderSide.BUY;
        BigDecimal secondPrice = BigDecimal.valueOf(100);
        BigDecimal secondQuantity = BigDecimal.valueOf(10000);

        submitOrder(tokenA, firstSide, firstPrice, firstQuantity, TradeRole.TAKER);
        submitOrder(tokenA, secondSide, secondPrice, secondQuantity, TradeRole.TAKER);
        submitOrder(tokenB, opposite(firstSide), firstPrice, secondQuantity, TradeRole.MAKER);
        submitOrder(tokenB, opposite(firstSide), firstPrice, firstQuantity, TradeRole.MAKER);

        simulator.apply(secondSide, secondPrice, secondQuantity, takerFeeRate);
        simulator.apply(firstSide, firstPrice, firstQuantity, takerFeeRate);
        ExpectedPosition expectedPosition = simulator.snapshot(firstPrice);
        ExpectedAccount expectedAccount = simulateAccount(expectedPosition);
        return new StepResult(expectedPosition, expectedAccount);
    }

    private void submitOrder(String token, OrderSide side, BigDecimal price, BigDecimal quantity, TradeRole role) {
        OrderClient.placeOrder(token, instrumentId, side, price.doubleValue(), quantity.intValueExact());
        pause(role == TradeRole.MAKER ? MAKER_DELAY_MS : TAKER_DELAY_MS);
    }
    
    private void verifyPosition(PositionResponse position, PositionSimulator simulator) {
        assertNotNull(position, "Position not found");

        PositionStatus expectedStatus = simulator.quantity.compareTo(BigDecimal.ZERO) == 0
            ? PositionStatus.CLOSED
            : PositionStatus.ACTIVE;
        BigDecimal expectedClosingReservedQuantity = BigDecimal.ZERO;

        assertEquals(expectedStatus, position.status(), "Status mismatch");
        assertEquals(simulator.side, position.side(), "Side mismatch");
        assertEquals(this.leverage, position.leverage(), "Leverage mismatch");

        BigDecimal expectedMargin = simulator.entryPrice
            .multiply(simulator.quantity)
            .multiply(contractSize)
            .multiply(imr);
        assertNear(position.margin(), expectedMargin, BigDecimal.valueOf(0.0001), "Margin mismatch");
        assertNear(position.entryPrice(), simulator.entryPrice, BigDecimal.valueOf(0.0001), "Entry price mismatch");
        assertNear(position.quantity(), simulator.quantity, BigDecimal.valueOf(0.0001), "Quantity mismatch");
        assertNear(position.closingReservedQuantity(),
            expectedClosingReservedQuantity, BigDecimal.valueOf(0.0001), "Close reserved mismatch");
        assertNear(position.markPrice(), simulator.lastMarkPrice, BigDecimal.valueOf(0.0001), "Mark price mismatch");

        BigDecimal notional = position.markPrice().multiply(position.quantity()).multiply(contractSize);
        BigDecimal equity = position.margin().add(position.unrealizedPnl());
        if (notional.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal expectedMarginRatio = equity.divide(notional, 10, RoundingMode.HALF_UP);
            assertNear(position.marginRatio(), expectedMarginRatio, BigDecimal.valueOf(0.001), "Margin ratio mismatch");
        }

        BigDecimal priceDiff = PositionSide.LONG == position.side()
            ? position.markPrice().subtract(position.entryPrice())
            : position.entryPrice().subtract(position.markPrice());
        BigDecimal expectedUpnl = priceDiff.multiply(position.quantity()).multiply(contractSize);
        assertNear(position.unrealizedPnl(), expectedUpnl, BigDecimal.valueOf(0.01), "Unrealized PnL mismatch");

        assertNear(position.cumRealizedPnl(), simulator.cumRealizedPnl, BigDecimal.valueOf(0.01), "Cum realized PnL mismatch");
        assertNear(position.cumFee(), simulator.cumFee, BigDecimal.valueOf(0.0001), "Cum fee mismatch");
        assertNear(position.cumFundingFee(),
            simulator.cumFundingFee, BigDecimal.valueOf(0.0001), "Cum funding fee mismatch");

        if (position.quantity().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal marginPerUnit = position.margin()
                .divide(position.quantity().multiply(contractSize), 10, RoundingMode.HALF_UP);
            BigDecimal calcLiq = PositionSide.LONG == position.side()
                ? position.entryPrice().subtract(marginPerUnit).divide(BigDecimal.ONE.subtract(mmr), 10, RoundingMode.HALF_UP)
                : position.entryPrice().add(marginPerUnit).divide(BigDecimal.ONE.add(mmr), 10, RoundingMode.HALF_UP);
            assertNotNull(position.liquidationPrice(), "Liquidation price should not be null");
            assertNear(position.liquidationPrice(), calcLiq, BigDecimal.valueOf(0.1), "Liquidation price mismatch");
        } else {
            assertTrue(position.liquidationPrice() == null || position.liquidationPrice().compareTo(BigDecimal.ZERO) == 0,
                       "Liquidation price should be null/0");
        }
    }

    private void verifyPosition(PositionResponse position, ExpectedPosition expected) {
        assertNotNull(position, "Position not found");
        
        assertEquals(expected.status, position.status(), "Status mismatch");
        assertEquals(expected.side, position.side(), "Side mismatch");
        assertEquals(this.leverage, position.leverage(), "Leverage mismatch");

        BigDecimal expectedMargin = expected.entryPrice
            .multiply(expected.quantity)
            .multiply(contractSize)
            .multiply(imr);
        assertNear(position.margin(), expectedMargin, BigDecimal.valueOf(0.0001), "Margin mismatch");
        assertNear(position.entryPrice(), expected.entryPrice, BigDecimal.valueOf(0.0001), "Entry price mismatch");
        assertNear(position.quantity(), expected.quantity, BigDecimal.valueOf(0.0001), "Quantity mismatch");
        assertNear(position.closingReservedQuantity(), expected.closingReservedQuantity, BigDecimal.valueOf(0.0001), "Close reserved mismatch");
        assertNear(position.markPrice(), expected.markPrice, BigDecimal.valueOf(0.0001), "Mark price mismatch");

        BigDecimal notional = position.markPrice().multiply(position.quantity()).multiply(contractSize);
        BigDecimal equity = position.margin().add(position.unrealizedPnl());
        if (notional.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal expectedMarginRatio = equity.divide(notional, 10, RoundingMode.HALF_UP);
            assertNear(position.marginRatio(), expectedMarginRatio, BigDecimal.valueOf(0.001), "Margin ratio mismatch");
        }

        BigDecimal priceDiff = PositionSide.LONG == position.side()
            ? position.markPrice().subtract(position.entryPrice())
            : position.entryPrice().subtract(position.markPrice());
        BigDecimal expectedUpnl = priceDiff.multiply(position.quantity()).multiply(contractSize);
        assertNear(position.unrealizedPnl(), expectedUpnl, BigDecimal.valueOf(0.01), "Unrealized PnL mismatch");

        assertNear(position.cumRealizedPnl(), expected.cumRealizedPnl, BigDecimal.valueOf(0.01), "Cum realized PnL mismatch");
        assertNear(position.cumFee(), expected.cumFee, BigDecimal.valueOf(0.0001), "Cum fee mismatch");
        assertNear(position.cumFundingFee(), expected.cumFundingFee, BigDecimal.valueOf(0.0001), "Cum funding fee mismatch");

        if (position.quantity().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal marginPerUnit = position.margin().divide(position.quantity().multiply(contractSize), 10, RoundingMode.HALF_UP);
            BigDecimal calcLiq = PositionSide.LONG == position.side()
                ? position.entryPrice().subtract(marginPerUnit).divide(BigDecimal.ONE.subtract(mmr), 10, RoundingMode.HALF_UP)
                : position.entryPrice().add(marginPerUnit).divide(BigDecimal.ONE.add(mmr), 10, RoundingMode.HALF_UP);
            assertNotNull(position.liquidationPrice(), "Liquidation price should not be null");
            assertNear(position.liquidationPrice(), calcLiq, BigDecimal.valueOf(0.1), "Liquidation price mismatch");
        } else {
            assertTrue(position.liquidationPrice() == null || position.liquidationPrice().compareTo(BigDecimal.ZERO) == 0,
                       "Liquidation price should be null/0");
        }
    }

    private void verifyAccount(AccountBalanceSheetResponse balance, ExpectedAccount expected) {
        List<AccountBalanceItem> assets = balance.assets();
        AccountBalanceItem spot = assets == null ? null : assets.stream()
            .filter(asset -> UserAccountCode.SPOT.equals(asset.accountCode()) && AssetSymbol.USDT.equals(asset.asset()))
            .findFirst()
            .orElse(null);
        AccountBalanceItem margin = assets == null ? null : assets.stream()
            .filter(asset -> UserAccountCode.MARGIN.equals(asset.accountCode())
                && asset.instrumentId() != null
                && instrumentId == asset.instrumentId().intValue()
                && AssetSymbol.USDT.equals(asset.asset()))
            .findFirst()
            .orElse(null);

        assertNotNull(spot, "Spot account not found");
        assertNotNull(margin, "Margin account not found");

        assertNear(spot.balance(),
            expected.spotBalance, BigDecimal.valueOf(0.0001), "Spot balance mismatch");
        assertNear(spot.available(),
            expected.spotAvailable, BigDecimal.valueOf(0.0001), "Spot available mismatch");
        assertNear(spot.reserved(),
            expected.spotReserved, BigDecimal.valueOf(0.0001), "Spot reserved mismatch");

        assertNear(margin.balance(),
            expected.marginBalance, BigDecimal.valueOf(0.0001), "Margin balance mismatch");
        assertNear(margin.available(),
            expected.marginAvailable, BigDecimal.valueOf(0.0001), "Margin available mismatch");
        assertNear(margin.reserved(),
            expected.marginReserved, BigDecimal.valueOf(0.0001), "Margin reserved mismatch");
    }

    private ExpectedAccount simulateAccount(ExpectedPosition expected) {
        BigDecimal expectedMargin = expected.entryPrice
            .multiply(expected.quantity)
            .multiply(contractSize)
            .multiply(imr);
        BigDecimal expectedSpotBalance = baseSpotBalance.subtract(expectedMargin).add(expected.cumRealizedPnl);
        return new ExpectedAccount(
            expectedSpotBalance,
            expectedSpotBalance,
            BigDecimal.ZERO,
            expectedMargin,
            expectedMargin,
            BigDecimal.ZERO
        );
    }

    private ExpectedAccount simulateAccount(PositionSimulator simulator) {
        BigDecimal expectedMargin = simulator.entryPrice
            .multiply(simulator.quantity)
            .multiply(contractSize)
            .multiply(imr);
        BigDecimal expectedSpotBalance = baseSpotBalance.subtract(expectedMargin).add(simulator.cumRealizedPnl);
        return new ExpectedAccount(
            expectedSpotBalance,
            expectedSpotBalance,
            BigDecimal.ZERO,
            expectedMargin,
            expectedMargin,
            BigDecimal.ZERO
        );
    }

    private static OrderSide opposite(OrderSide side) {
        return side == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;
    }

    private static void pause(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void assertNear(BigDecimal actual, BigDecimal expected, BigDecimal tolerance, String message) {
        BigDecimal diff = actual.subtract(expected).abs();
        assertTrue(diff.compareTo(tolerance) <= 0,
            message + " expected=" + expected + " actual=" + actual);
    }

    private enum TradeRole {
        TAKER,
        MAKER
    }

    private record StepResult(ExpectedPosition expectedPosition, ExpectedAccount expectedAccount) {
    }

    private record ExpectedAccount(BigDecimal spotBalance,
                                   BigDecimal spotAvailable,
                                   BigDecimal spotReserved,
                                   BigDecimal marginBalance,
                                   BigDecimal marginAvailable,
                                   BigDecimal marginReserved) {
    }


    private static class PositionSimulator {
        private final BigDecimal contractSize;
        private PositionSide side;
        private BigDecimal quantity;
        private BigDecimal entryPrice;
        private BigDecimal cumRealizedPnl;
        private BigDecimal cumFee;
        private BigDecimal cumFundingFee;
        private BigDecimal lastMarkPrice;

        private PositionSimulator(BigDecimal contractSize) {
            this.contractSize = contractSize;
            this.side = PositionSide.LONG;
            this.quantity = BigDecimal.ZERO;
            this.entryPrice = BigDecimal.ZERO;
            this.cumRealizedPnl = BigDecimal.ZERO;
            this.cumFee = BigDecimal.ZERO;
            this.cumFundingFee = BigDecimal.ZERO;
            this.lastMarkPrice = BigDecimal.ZERO;
        }

        private void apply(OrderSide orderSide, BigDecimal price, BigDecimal fillQuantity, BigDecimal feeRate) {
            BigDecimal fee = fillQuantity.multiply(contractSize)
                .multiply(price)
                .multiply(feeRate);
            lastMarkPrice = price;

            if (quantity.compareTo(BigDecimal.ZERO) == 0) {
                side = orderSide == OrderSide.BUY ? PositionSide.LONG : PositionSide.SHORT;
                entryPrice = price;
                quantity = fillQuantity;
                cumFee = BigDecimal.ZERO.add(fee);
                cumRealizedPnl = fee.negate();
                cumFundingFee = BigDecimal.ZERO;
                return;
            }

            boolean sameDirection = (side == PositionSide.LONG && orderSide == OrderSide.BUY)
                || (side == PositionSide.SHORT && orderSide == OrderSide.SELL);
            if (sameDirection) {
                BigDecimal totalCost = entryPrice.multiply(quantity).add(price.multiply(fillQuantity));
                BigDecimal totalQty = quantity.add(fillQuantity);
                entryPrice = totalCost.divide(totalQty, 10, RoundingMode.HALF_UP);
                quantity = totalQty;
                cumFee = cumFee.add(fee);
                cumRealizedPnl = cumRealizedPnl.subtract(fee);
                return;
            }

            BigDecimal closedQty = quantity.min(fillQuantity);
            BigDecimal realized = side == PositionSide.LONG
                ? price.subtract(entryPrice).multiply(closedQty).multiply(contractSize)
                : entryPrice.subtract(price).multiply(closedQty).multiply(contractSize);
            quantity = quantity.subtract(closedQty);

            BigDecimal remaining = fillQuantity.subtract(closedQty);
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                side = orderSide == OrderSide.BUY ? PositionSide.LONG : PositionSide.SHORT;
                entryPrice = price;
                quantity = remaining;
                cumFee = fee;
                cumRealizedPnl = fee.negate();
                cumFundingFee = BigDecimal.ZERO;
                return;
            }

            cumFee = cumFee.add(fee);
            cumRealizedPnl = cumRealizedPnl.add(realized).subtract(fee);
        }

        private ExpectedPosition snapshot(BigDecimal markPrice) {
            this.lastMarkPrice = markPrice;
            if (quantity.compareTo(BigDecimal.ZERO) == 0) {
                return new ExpectedPosition(PositionStatus.CLOSED,
                    side,
                    BigDecimal.ZERO,
                    entryPrice,
                    BigDecimal.ZERO,
                    markPrice,
                    cumRealizedPnl,
                    cumFee,
                    cumFundingFee);
            }
            return new ExpectedPosition(PositionStatus.ACTIVE,
                side,
                quantity,
                entryPrice,
                BigDecimal.ZERO,
                markPrice,
                cumRealizedPnl,
                cumFee,
                cumFundingFee);
        }
    }

    private record ExpectedPosition(PositionStatus status,
                                    PositionSide side,
                                    BigDecimal quantity,
                                    BigDecimal entryPrice,
                                    BigDecimal closingReservedQuantity,
                                    BigDecimal markPrice,
                                    BigDecimal cumRealizedPnl,
                                    BigDecimal cumFee,
                                    BigDecimal cumFundingFee) {
    }
}
