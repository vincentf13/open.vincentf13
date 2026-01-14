package open.vincentf13.exchange.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
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

class PositionApiRestAssuredTest {
    private static final String DEFAULT_GATEWAY_HOST = "http://localhost:12345";
    private static final int DEFAULT_INSTRUMENT_ID = 10001;
    private static final String DEFAULT_PASSWORD = "12345678";
    private static final String EMAIL_A = "c.p.kevinf13@gmail.com";
    private static final String EMAIL_B = "c.p.kevinf13-2@gmail.com";
    private static final String EMAIL_C = "c.p.kevinf13-3@gmail.com";
    private static final long MAKER_DELAY_MS = 2000;
    private static final long TAKER_DELAY_MS = 5000;

    private AuthClient authClient;
    private SystemClient systemClient;
    private AdminClient adminClient;
    private RiskClient riskClient;
    private AccountClient accountClient;
    private OrderClient orderClient;
    private PositionClient positionClient;
    private int instrumentId;
    private String tokenA;
    private String tokenB;
    private String tokenC;
    private BigDecimal contractSize;
    private BigDecimal makerFeeRate;
    private BigDecimal takerFeeRate;
    private BigDecimal leverage;
    private BigDecimal mmr;
    private BigDecimal imr;
    private BigDecimal baseSpotBalance;

    @BeforeEach
    void setUp() {
        authClient = new AuthClient(DEFAULT_GATEWAY_HOST);
        systemClient = new SystemClient(DEFAULT_GATEWAY_HOST);
        adminClient = new AdminClient(DEFAULT_GATEWAY_HOST);
        riskClient = new RiskClient(DEFAULT_GATEWAY_HOST);
        accountClient = new AccountClient(DEFAULT_GATEWAY_HOST);
        orderClient = new OrderClient(DEFAULT_GATEWAY_HOST);
        positionClient = new PositionClient(DEFAULT_GATEWAY_HOST);
        systemClient.resetData();

        String tokenA = authClient.login(EMAIL_A, DEFAULT_PASSWORD);
        String tokenB = authClient.login(EMAIL_B, DEFAULT_PASSWORD);
        String tokenC = authClient.login(EMAIL_C, DEFAULT_PASSWORD);

        accountClient.deposit(tokenA, 10000);
        accountClient.deposit(tokenB, 10000);
        accountClient.deposit(tokenC, 10000);

        InstrumentDetailResponse instrument = adminClient.getInstrument(tokenA, DEFAULT_INSTRUMENT_ID);
        RiskLimitResponse risk = riskClient.getRiskLimit(tokenA, DEFAULT_INSTRUMENT_ID);

        contractSize = getBigDecimalOrThow(instrument.contractSize(), "contractSize");
        makerFeeRate = getBigDecimalOrThow(instrument.makerFeeRate(), "makerFeeRate");
        takerFeeRate = getBigDecimalOrThow(instrument.takerFeeRate(), "takerFeeRate");
        leverage = getBigDecimalOrThow(instrument.defaultLeverage(), "defaultLeverage");
        mmr = getBigDecimalOrThow(risk.maintenanceMarginRate(), "maintenanceMarginRate");
        imr = getBigDecimalOrThow(risk.initialMarginRate(), "initialMarginRate");

        AccountBalanceItem spot = accountClient.getSpotAccount(tokenA);
        assertNotNull(spot, "Spot account not found for baseline");
        baseSpotBalance = getBigDecimalOrThow(spot.balance(), "spot.balance");
        
        instrumentId = DEFAULT_INSTRUMENT_ID;
        this.tokenA = tokenA;
        this.tokenB = tokenB;
        this.tokenC = tokenC;
    }

    @Test
    void scenario1_longMakerFlow() {
        PositionSimulator simulator = new PositionSimulator(contractSize);

        OrderSide openSide = OrderSide.BUY;
        double openPrice = 100;
        int openQuantity = 5000;
        submitOrder(Counterparty.A, openSide, openPrice, openQuantity, TradeRole.TAKER);
        submitOrder(Counterparty.B, opposite(openSide), openPrice, openQuantity, TradeRole.MAKER);
        ExpectedPosition openPosition = simulatePosition(simulator, openSide, openPrice, true, openQuantity);
        ExpectedAccount openAccount = simulateAccount(openPosition);
        verifyPosition(positionClient.findPosition(tokenA, instrumentId), openPosition);
        verifyAccount(accountClient.getBalanceSheet(tokenA), openAccount);

        OrderSide reduceSide = OrderSide.SELL;
        double reducePrice = 101;
        int reduceQuantity = 3000;
        submitOrder(Counterparty.A, reduceSide, reducePrice, reduceQuantity, TradeRole.TAKER);
        submitOrder(Counterparty.B, opposite(reduceSide), reducePrice, reduceQuantity, TradeRole.MAKER);
        ExpectedPosition reducePosition = simulatePosition(simulator, reduceSide, reducePrice, true, reduceQuantity);
        ExpectedAccount reduceAccount = simulateAccount(reducePosition);
        verifyPosition(positionClient.findPosition(tokenA, instrumentId), reducePosition);
        verifyAccount(accountClient.getBalanceSheet(tokenA), reduceAccount);

        OrderSide increaseSide = OrderSide.BUY;
        double increasePrice = 102;
        int increaseQuantity = 2000;
        submitOrder(Counterparty.A, increaseSide, increasePrice, increaseQuantity, TradeRole.TAKER);
        submitOrder(Counterparty.B, opposite(increaseSide), increasePrice, increaseQuantity, TradeRole.MAKER);
        ExpectedPosition increasePosition = simulatePosition(simulator, increaseSide, increasePrice, true, increaseQuantity);
        ExpectedAccount increaseAccount = simulateAccount(increasePosition);
        verifyPosition(positionClient.findPosition(tokenA, instrumentId), increasePosition);
        verifyAccount(accountClient.getBalanceSheet(tokenA), increaseAccount);

        OrderSide closeSide = OrderSide.SELL;
        double closePrice = 99;
        int closeQuantity = 4000;
        submitOrder(Counterparty.A, closeSide, closePrice, closeQuantity, TradeRole.TAKER);
        submitOrder(Counterparty.B, opposite(closeSide), closePrice, closeQuantity, TradeRole.MAKER);
        ExpectedPosition closePosition = simulatePosition(simulator, closeSide, closePrice, true, closeQuantity);
        ExpectedAccount closeAccount = simulateAccount(closePosition);
        verifyPosition(positionClient.findPosition(tokenA, instrumentId), closePosition);
        verifyAccount(accountClient.getBalanceSheet(tokenA), closeAccount);

        OrderSide reopenSide = OrderSide.BUY;
        double reopenPrice = 98;
        int reopenQuantity = 5000;
        submitOrder(Counterparty.A, reopenSide, reopenPrice, reopenQuantity, TradeRole.TAKER);
        submitOrder(Counterparty.B, opposite(reopenSide), reopenPrice, reopenQuantity, TradeRole.MAKER);
        ExpectedPosition reopenPosition = simulatePosition(simulator, reopenSide, reopenPrice, true, reopenQuantity);
        ExpectedAccount reopenAccount = simulateAccount(reopenPosition);
        verifyPosition(positionClient.findPosition(tokenA, instrumentId), reopenPosition);
        verifyAccount(accountClient.getBalanceSheet(tokenA), reopenAccount);

        StepResult flipResult = executeTradeWithTwoCounterparties(simulator, OrderSide.SELL, 100, 10000);
        verifyPosition(positionClient.findPosition(tokenA, instrumentId), flipResult.expectedPosition);
        verifyAccount(accountClient.getBalanceSheet(tokenA), flipResult.expectedAccount);

        StepResult concurrentResult = executeConcurrentFlip(simulator);
        verifyPosition(positionClient.findPosition(tokenA, instrumentId), concurrentResult.expectedPosition);
        verifyAccount(accountClient.getBalanceSheet(tokenA), concurrentResult.expectedAccount);
    }

    private StepResult executeTradeWithTwoCounterparties(PositionSimulator simulator, OrderSide side, double price, int quantity) {
        submitOrder(Counterparty.A, side, price, quantity, TradeRole.TAKER);
        submitOrder(Counterparty.B, opposite(side), price, 5000, TradeRole.MAKER);
        submitOrder(Counterparty.C, opposite(side), price, 5000, TradeRole.MAKER);
        simulatorApply(simulator, side, price, true, 5000);
        simulatorApply(simulator, side, price, true, 5000);
        ExpectedPosition expectedPosition = simulator.snapshot(price);
        ExpectedAccount expectedAccount = simulateAccount(expectedPosition);
        return new StepResult(expectedPosition, expectedAccount);
    }

    private StepResult executeConcurrentFlip(PositionSimulator simulator) {
        OrderSide firstSide = OrderSide.BUY;
        double firstPrice = 100;
        int firstQuantity = 3000;
        OrderSide secondSide = OrderSide.BUY;
        double secondPrice = 100;
        int secondQuantity = 10000;

        submitOrder(Counterparty.A, firstSide, firstPrice, firstQuantity, TradeRole.TAKER);
        submitOrder(Counterparty.A, secondSide, secondPrice, secondQuantity, TradeRole.TAKER);
        submitOrder(Counterparty.B, opposite(firstSide), firstPrice, secondQuantity, TradeRole.MAKER);
        submitOrder(Counterparty.B, opposite(firstSide), firstPrice, firstQuantity, TradeRole.MAKER);

        simulatorApply(simulator, secondSide, secondPrice, true, secondQuantity);
        simulatorApply(simulator, firstSide, firstPrice, true, firstQuantity);
        ExpectedPosition expectedPosition = simulator.snapshot(firstPrice);
        ExpectedAccount expectedAccount = simulateAccount(expectedPosition);
        return new StepResult(expectedPosition, expectedAccount);
    }

    private void submitOrder(Counterparty counterparty, OrderSide side, double price, int quantity, TradeRole role) {
        String token = switch (counterparty) {
            case A -> tokenA;
            case B -> tokenB;
            case C -> tokenC;
        };
        orderClient.placeOrder(token, instrumentId, side, price, quantity);
        pause(role == TradeRole.MAKER ? MAKER_DELAY_MS : TAKER_DELAY_MS);
    }

    private void verifyPosition(PositionResponse position, ExpectedPosition expected) {
        assertNotNull(position, "Position not found");

        PositionStatus status = position.status();
        PositionSide side = position.side();
        BigDecimal leverage = getBigDecimalOrThow(position.leverage(), "position.leverage");
        BigDecimal margin = getBigDecimalOrThow(position.margin(), "position.margin");
        BigDecimal entryPrice = getBigDecimalOrThow(position.entryPrice(), "position.entryPrice");
        BigDecimal quantity = getBigDecimalOrThow(position.quantity(), "position.quantity");
        BigDecimal closingReserved = getBigDecimalOrThow(position.closingReservedQuantity(), "position.closingReservedQuantity");
        BigDecimal markPrice = getBigDecimalOrThow(position.markPrice(), "position.markPrice");
        BigDecimal marginRatio = getBigDecimalOrThow(position.marginRatio(), "position.marginRatio");
        BigDecimal unrealizedPnl = getBigDecimalOrThow(position.unrealizedPnl(), "position.unrealizedPnl");
        BigDecimal cumRealizedPnl = getBigDecimalOrThow(position.cumRealizedPnl(), "position.cumRealizedPnl");
        BigDecimal cumFee = getBigDecimalOrThow(position.cumFee(), "position.cumFee");
        BigDecimal cumFundingFee = getBigDecimalOrThow(position.cumFundingFee(), "position.cumFundingFee");
        BigDecimal liquidationPrice = position.liquidationPrice();

        assertEquals(expected.status, status, "Status mismatch");
        assertEquals(expected.side, side, "Side mismatch");
        assertNear(leverage, this.leverage, BigDecimal.valueOf(0.0001), "Leverage mismatch");

        BigDecimal expectedMargin = expected.entryPrice
            .multiply(expected.quantity)
            .multiply(contractSize)
            .multiply(imr);
        assertNear(margin, expectedMargin, BigDecimal.valueOf(0.0001), "Margin mismatch");
        assertNear(entryPrice, expected.entryPrice, BigDecimal.valueOf(0.0001), "Entry price mismatch");
        assertNear(quantity, expected.quantity, BigDecimal.valueOf(0.0001), "Quantity mismatch");
        assertNear(closingReserved, expected.closingReservedQuantity, BigDecimal.valueOf(0.0001), "Close reserved mismatch");
        assertNear(markPrice, expected.markPrice, BigDecimal.valueOf(0.0001), "Mark price mismatch");

        BigDecimal notional = markPrice.multiply(quantity).multiply(contractSize);
        BigDecimal equity = margin.add(unrealizedPnl);
        if (notional.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal expectedMarginRatio = equity.divide(notional, 10, RoundingMode.HALF_UP);
            assertNear(marginRatio, expectedMarginRatio, BigDecimal.valueOf(0.001), "Margin ratio mismatch");
        }

        BigDecimal priceDiff = PositionSide.LONG == side
            ? markPrice.subtract(entryPrice)
            : entryPrice.subtract(markPrice);
        BigDecimal expectedUpnl = priceDiff.multiply(quantity).multiply(contractSize);
        assertNear(unrealizedPnl, expectedUpnl, BigDecimal.valueOf(0.01), "Unrealized PnL mismatch");

        assertNear(cumRealizedPnl, expected.cumRealizedPnl, BigDecimal.valueOf(0.01), "Cum realized PnL mismatch");
        assertNear(cumFee, expected.cumFee, BigDecimal.valueOf(0.0001), "Cum fee mismatch");
        assertNear(cumFundingFee, expected.cumFundingFee, BigDecimal.valueOf(0.0001), "Cum funding fee mismatch");

        if (quantity.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal marginPerUnit = margin.divide(quantity.multiply(contractSize), 10, RoundingMode.HALF_UP);
            BigDecimal calcLiq = PositionSide.LONG == side
                ? entryPrice.subtract(marginPerUnit).divide(BigDecimal.ONE.subtract(mmr), 10, RoundingMode.HALF_UP)
                : entryPrice.add(marginPerUnit).divide(BigDecimal.ONE.add(mmr), 10, RoundingMode.HALF_UP);
            assertNotNull(liquidationPrice, "Liquidation price should not be null");
            assertNear(liquidationPrice, calcLiq, BigDecimal.valueOf(0.1), "Liquidation price mismatch");
        } else {
            assertTrue(liquidationPrice == null || liquidationPrice.compareTo(BigDecimal.ZERO) == 0,
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

        assertNear(getBigDecimalOrThow(spot.balance(), "spot.balance"),
            expected.spotBalance, BigDecimal.valueOf(0.0001), "Spot balance mismatch");
        assertNear(getBigDecimalOrThow(spot.available(), "spot.available"),
            expected.spotAvailable, BigDecimal.valueOf(0.0001), "Spot available mismatch");
        assertNear(getBigDecimalOrThow(spot.reserved(), "spot.reserved"),
            expected.spotReserved, BigDecimal.valueOf(0.0001), "Spot reserved mismatch");

        assertNear(getBigDecimalOrThow(margin.balance(), "margin.balance"),
            expected.marginBalance, BigDecimal.valueOf(0.0001), "Margin balance mismatch");
        assertNear(getBigDecimalOrThow(margin.available(), "margin.available"),
            expected.marginAvailable, BigDecimal.valueOf(0.0001), "Margin available mismatch");
        assertNear(getBigDecimalOrThow(margin.reserved(), "margin.reserved"),
            expected.marginReserved, BigDecimal.valueOf(0.0001), "Margin reserved mismatch");
    }

    private void simulatorApply(PositionSimulator simulator, OrderSide side, double price, boolean aIsTaker, int quantity) {
        BigDecimal feeRate = aIsTaker ? takerFeeRate : makerFeeRate;
        simulator.apply(side, price, quantity, feeRate);
    }

    private ExpectedPosition simulatePosition(PositionSimulator simulator,
                                              OrderSide side,
                                              double price,
                                              boolean aIsTaker,
                                              int quantity) {
        simulatorApply(simulator, side, price, aIsTaker, quantity);
        return simulator.snapshot(price);
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

    private static BigDecimal getBigDecimalOrThow(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private static void assertNear(BigDecimal actual, BigDecimal expected, BigDecimal tolerance, String message) {
        BigDecimal diff = actual.subtract(expected).abs();
        assertTrue(diff.compareTo(tolerance) <= 0,
            message + " expected=" + expected + " actual=" + actual);
    }

    private enum Counterparty {
        A,
        B,
        C
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

        private void apply(OrderSide orderSide, double price, int fillQuantity, BigDecimal feeRate) {
            BigDecimal qty = BigDecimal.valueOf(fillQuantity);
            BigDecimal priceValue = BigDecimal.valueOf(price);
            BigDecimal fee = qty.multiply(contractSize)
                .multiply(priceValue)
                .multiply(feeRate);
            lastMarkPrice = priceValue;

            if (quantity.compareTo(BigDecimal.ZERO) == 0) {
                side = orderSide == OrderSide.BUY ? PositionSide.LONG : PositionSide.SHORT;
                entryPrice = priceValue;
                quantity = qty;
                cumFee = BigDecimal.ZERO.add(fee);
                cumRealizedPnl = fee.negate();
                cumFundingFee = BigDecimal.ZERO;
                return;
            }

            boolean sameDirection = (side == PositionSide.LONG && orderSide == OrderSide.BUY)
                || (side == PositionSide.SHORT && orderSide == OrderSide.SELL);
            if (sameDirection) {
                BigDecimal totalCost = entryPrice.multiply(quantity).add(priceValue.multiply(qty));
                BigDecimal totalQty = quantity.add(qty);
                entryPrice = totalCost.divide(totalQty, 10, RoundingMode.HALF_UP);
                quantity = totalQty;
                cumFee = cumFee.add(fee);
                cumRealizedPnl = cumRealizedPnl.subtract(fee);
                return;
            }

            BigDecimal closedQty = quantity.min(qty);
            BigDecimal realized = side == PositionSide.LONG
                ? priceValue.subtract(entryPrice).multiply(closedQty).multiply(contractSize)
                : entryPrice.subtract(priceValue).multiply(closedQty).multiply(contractSize);
            quantity = quantity.subtract(closedQty);

            BigDecimal remaining = qty.subtract(closedQty);
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                side = orderSide == OrderSide.BUY ? PositionSide.LONG : PositionSide.SHORT;
                entryPrice = priceValue;
                quantity = remaining;
                cumFee = fee;
                cumRealizedPnl = fee.negate();
                cumFundingFee = BigDecimal.ZERO;
                return;
            }

            cumFee = cumFee.add(fee);
            cumRealizedPnl = cumRealizedPnl.add(realized).subtract(fee);
        }

        private ExpectedPosition snapshot(double markPrice) {
            this.lastMarkPrice = BigDecimal.valueOf(markPrice);
            if (quantity.compareTo(BigDecimal.ZERO) == 0) {
                return new ExpectedPosition(PositionStatus.CLOSED,
                    side,
                    BigDecimal.ZERO,
                    entryPrice,
                    BigDecimal.ZERO,
                    BigDecimal.valueOf(markPrice),
                    cumRealizedPnl,
                    cumFee,
                    cumFundingFee);
            }
            return new ExpectedPosition(PositionStatus.ACTIVE,
                side,
                quantity,
                entryPrice,
                BigDecimal.ZERO,
                BigDecimal.valueOf(markPrice),
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
