package open.vincentf13.exchange.test;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionApiRestAssuredTest {
    private static final String DEFAULT_GATEWAY_HOST = "http://localhost:12345";
    private static final int DEFAULT_INSTRUMENT_ID = 10001;
    private static final String DEFAULT_PASSWORD = "12345678";
    private static final String EMAIL_A = "c.p.kevinf13@gmail.com";
    private static final String EMAIL_B = "c.p.kevinf13-2@gmail.com";
    private static final String EMAIL_C = "c.p.kevinf13-3@gmail.com";
    private static final long MAKER_DELAY_MS = 2000;
    private static final long TAKER_DELAY_MS = 5000;

    private ApiClient api;
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
        api = new ApiClient(DEFAULT_GATEWAY_HOST);
        api.resetData();
        
        String tokenA1 = api.login(EMAIL_A, DEFAULT_PASSWORD);
        String tokenB1 = api.login(EMAIL_B, DEFAULT_PASSWORD);
        String tokenC1 = api.login(EMAIL_C, DEFAULT_PASSWORD);
        
        api.deposit(tokenA1, 10000);
        api.deposit(tokenB1, 10000);
        api.deposit(tokenC1, 10000);
        
        Map<String, Object> instrument = api.findInstrument(tokenA1, DEFAULT_INSTRUMENT_ID);
        Map<String, Object> risk = api.getRiskLimit(tokenA1, DEFAULT_INSTRUMENT_ID);
        
        BigDecimal contractSize1 = getBigDecimalOrThrow(instrument.get("contractSize"), "contractSize");
        BigDecimal makerFeeRate1 = getBigDecimalOrThrow(instrument.get("makerFeeRate"), "makerFeeRate");
        BigDecimal takerFeeRate1 = getBigDecimalOrThrow(instrument.get("takerFeeRate"), "takerFeeRate");
        BigDecimal leverage1 = getBigDecimalOrThrow(instrument.get("defaultLeverage"), "defaultLeverage");
        BigDecimal mmr1 = getBigDecimalOrThrow(risk.get("maintenanceMarginRate"), "maintenanceMarginRate");
        BigDecimal imr1 = getBigDecimalOrThrow(risk.get("initialMarginRate"), "initialMarginRate");
        
        Map<String, Object> balanceSheet = api.getBalanceSheet(tokenA1);
        List<Map<String, Object>> assets = castList(balanceSheet.get("assets"));
        Map<String, Object> spot = assets.stream()
            .filter(asset -> "SPOT".equals(asset.get("accountCode")) && "USDT".equals(asset.get("asset")))
            .findFirst()
            .orElse(null);
        assertNotNull(spot, "Spot account not found for baseline");
        BigDecimal baseSpotBalance1 = getBigDecimalOrThrow(spot.get("balance"), "spot.balance");
        
        instrumentId = DEFAULT_INSTRUMENT_ID;
        tokenA = tokenA1;
        tokenB = tokenB1;
        tokenC = tokenC1;
        contractSize = contractSize1;
        makerFeeRate = makerFeeRate1;
        takerFeeRate = takerFeeRate1;
        leverage = leverage1;
        mmr = mmr1;
        imr = imr1;
        baseSpotBalance = baseSpotBalance1;
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
        verifyPosition(api.findPosition(tokenA, instrumentId), openPosition);
        verifyAccount(api.getBalanceSheet(tokenA), openAccount);

        OrderSide reduceSide = OrderSide.SELL;
        double reducePrice = 101;
        int reduceQuantity = 3000;
        submitOrder(Counterparty.A, reduceSide, reducePrice, reduceQuantity, TradeRole.TAKER);
        submitOrder(Counterparty.B, opposite(reduceSide), reducePrice, reduceQuantity, TradeRole.MAKER);
        ExpectedPosition reducePosition = simulatePosition(simulator, reduceSide, reducePrice, true, reduceQuantity);
        ExpectedAccount reduceAccount = simulateAccount(reducePosition);
        verifyPosition(api.findPosition(tokenA, instrumentId), reducePosition);
        verifyAccount(api.getBalanceSheet(tokenA), reduceAccount);

        OrderSide increaseSide = OrderSide.BUY;
        double increasePrice = 102;
        int increaseQuantity = 2000;
        submitOrder(Counterparty.A, increaseSide, increasePrice, increaseQuantity, TradeRole.TAKER);
        submitOrder(Counterparty.B, opposite(increaseSide), increasePrice, increaseQuantity, TradeRole.MAKER);
        ExpectedPosition increasePosition = simulatePosition(simulator, increaseSide, increasePrice, true, increaseQuantity);
        ExpectedAccount increaseAccount = simulateAccount(increasePosition);
        verifyPosition(api.findPosition(tokenA, instrumentId), increasePosition);
        verifyAccount(api.getBalanceSheet(tokenA), increaseAccount);

        OrderSide closeSide = OrderSide.SELL;
        double closePrice = 99;
        int closeQuantity = 4000;
        submitOrder(Counterparty.A, closeSide, closePrice, closeQuantity, TradeRole.TAKER);
        submitOrder(Counterparty.B, opposite(closeSide), closePrice, closeQuantity, TradeRole.MAKER);
        ExpectedPosition closePosition = simulatePosition(simulator, closeSide, closePrice, true, closeQuantity);
        ExpectedAccount closeAccount = simulateAccount(closePosition);
        verifyPosition(api.findPosition(tokenA, instrumentId), closePosition);
        verifyAccount(api.getBalanceSheet(tokenA), closeAccount);

        OrderSide reopenSide = OrderSide.BUY;
        double reopenPrice = 98;
        int reopenQuantity = 5000;
        submitOrder(Counterparty.A, reopenSide, reopenPrice, reopenQuantity, TradeRole.TAKER);
        submitOrder(Counterparty.B, opposite(reopenSide), reopenPrice, reopenQuantity, TradeRole.MAKER);
        ExpectedPosition reopenPosition = simulatePosition(simulator, reopenSide, reopenPrice, true, reopenQuantity);
        ExpectedAccount reopenAccount = simulateAccount(reopenPosition);
        verifyPosition(api.findPosition(tokenA, instrumentId), reopenPosition);
        verifyAccount(api.getBalanceSheet(tokenA), reopenAccount);

        StepResult flipResult = executeTradeWithTwoCounterparties(simulator, OrderSide.SELL, 100, 10000);
        verifyPosition(api.findPosition(tokenA, instrumentId), flipResult.expectedPosition);
        verifyAccount(api.getBalanceSheet(tokenA), flipResult.expectedAccount);

        StepResult concurrentResult = executeConcurrentFlip(simulator);
        verifyPosition(api.findPosition(tokenA, instrumentId), concurrentResult.expectedPosition);
        verifyAccount(api.getBalanceSheet(tokenA), concurrentResult.expectedAccount);
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
        api.placeOrder(token, instrumentId, side, price, quantity);
        pause(role == TradeRole.MAKER ? MAKER_DELAY_MS : TAKER_DELAY_MS);
    }

    private void verifyPosition(Map<String, Object> position, ExpectedPosition expected) {
        assertNotNull(position, "Position not found");

        String status = Objects.toString(position.get("status"), null);
        String side = Objects.toString(position.get("side"), null);
        BigDecimal leverage = getBigDecimalOrThrow(position.get("leverage"), "position.leverage");
        BigDecimal margin = getBigDecimalOrThrow(position.get("margin"), "position.margin");
        BigDecimal entryPrice = getBigDecimalOrThrow(position.get("entryPrice"), "position.entryPrice");
        BigDecimal quantity = getBigDecimalOrThrow(position.get("quantity"), "position.quantity");
        BigDecimal closingReserved = getBigDecimalOrThrow(position.get("closingReservedQuantity"), "position.closingReservedQuantity");
        BigDecimal markPrice = getBigDecimalOrThrow(position.get("markPrice"), "position.markPrice");
        BigDecimal marginRatio = getBigDecimalOrThrow(position.get("marginRatio"), "position.marginRatio");
        BigDecimal unrealizedPnl = getBigDecimalOrThrow(position.get("unrealizedPnl"), "position.unrealizedPnl");
        BigDecimal cumRealizedPnl = getBigDecimalOrThrow(position.get("cumRealizedPnl"), "position.cumRealizedPnl");
        BigDecimal cumFee = getBigDecimalOrThrow(position.get("cumFee"), "position.cumFee");
        BigDecimal cumFundingFee = getBigDecimalOrThrow(position.get("cumFundingFee"), "position.cumFundingFee");
        BigDecimal liquidationPrice = position.get("liquidationPrice") == null
            ? null
            : getBigDecimalOrThrow(position.get("liquidationPrice"), "position.liquidationPrice");

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

        BigDecimal priceDiff = "LONG".equals(side)
            ? markPrice.subtract(entryPrice)
            : entryPrice.subtract(markPrice);
        BigDecimal expectedUpnl = priceDiff.multiply(quantity).multiply(contractSize);
        assertNear(unrealizedPnl, expectedUpnl, BigDecimal.valueOf(0.01), "Unrealized PnL mismatch");

        assertNear(cumRealizedPnl, expected.cumRealizedPnl, BigDecimal.valueOf(0.01), "Cum realized PnL mismatch");
        assertNear(cumFee, expected.cumFee, BigDecimal.valueOf(0.0001), "Cum fee mismatch");
        assertNear(cumFundingFee, expected.cumFundingFee, BigDecimal.valueOf(0.0001), "Cum funding fee mismatch");

        if (quantity.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal marginPerUnit = margin.divide(quantity.multiply(contractSize), 10, RoundingMode.HALF_UP);
            BigDecimal calcLiq = "LONG".equals(side)
                ? entryPrice.subtract(marginPerUnit).divide(BigDecimal.ONE.subtract(mmr), 10, RoundingMode.HALF_UP)
                : entryPrice.add(marginPerUnit).divide(BigDecimal.ONE.add(mmr), 10, RoundingMode.HALF_UP);
            assertNotNull(liquidationPrice, "Liquidation price should not be null");
            assertNear(liquidationPrice, calcLiq, BigDecimal.valueOf(0.1), "Liquidation price mismatch");
        } else {
            assertTrue(liquidationPrice == null || liquidationPrice.compareTo(BigDecimal.ZERO) == 0,
                "Liquidation price should be null/0");
        }
    }

    private void verifyAccount(Map<String, Object> balance, ExpectedAccount expected) {
        List<Map<String, Object>> assets = castList(balance.get("assets"));
        Map<String, Object> spot = assets.stream()
            .filter(asset -> "SPOT".equals(asset.get("accountCode")) && "USDT".equals(asset.get("asset")))
            .findFirst()
            .orElse(null);
        Map<String, Object> margin = assets.stream()
            .filter(asset -> "MARGIN".equals(asset.get("accountCode"))
                && instrumentId == parseInt(asset.get("instrumentId"))
                && "USDT".equals(asset.get("asset")))
            .findFirst()
            .orElse(null);

        assertNotNull(spot, "Spot account not found");
        assertNotNull(margin, "Margin account not found");

        assertNear(getBigDecimalOrThrow(spot.get("balance"), "spot.balance"),
            expected.spotBalance, BigDecimal.valueOf(0.0001), "Spot balance mismatch");
        assertNear(getBigDecimalOrThrow(spot.get("available"), "spot.available"),
            expected.spotAvailable, BigDecimal.valueOf(0.0001), "Spot available mismatch");
        assertNear(getBigDecimalOrThrow(spot.get("reserved"), "spot.reserved"),
            expected.spotReserved, BigDecimal.valueOf(0.0001), "Spot reserved mismatch");

        assertNear(getBigDecimalOrThrow(margin.get("balance"), "margin.balance"),
            expected.marginBalance, BigDecimal.valueOf(0.0001), "Margin balance mismatch");
        assertNear(getBigDecimalOrThrow(margin.get("available"), "margin.available"),
            expected.marginAvailable, BigDecimal.valueOf(0.0001), "Margin available mismatch");
        assertNear(getBigDecimalOrThrow(margin.get("reserved"), "margin.reserved"),
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

    private static int parseInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static BigDecimal getBigDecimalOrThrow(Object value, String fieldName) {
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

    private static List<Map<String, Object>> castList(Object value) {
        if (value == null) {
            return List.of();
        }
        return (List<Map<String, Object>>) value;
    }

    private enum OrderSide {
        BUY,
        SELL
    }

    private enum PositionSide {
        LONG,
        SHORT
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

    private static class ApiClient {
        private final String baseUri;

        private ApiClient(String baseUri) {
            this.baseUri = baseUri;
            RestAssured.baseURI = baseUri;
        }

        private void resetData() {
            given()
                .when()
                .post("/admin/api/admin/system/reset-data")
                .then()
                .statusCode(200);
        }
        
        private String login(String email, String password) {
            Response response = given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", password))
                .when()
                .post("/auth/api/login");
            response.then().statusCode(200);
            String token = response.jsonPath().getString("data.jwtToken");
            assertNotNull(token, "Missing jwtToken");
            return token;
        }

        private void deposit(String token, double amount) {
            String txId = "setup-dep-" + UUID.randomUUID();
            String creditedAt = Instant.now().toString();
            given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of(
                    "asset", "USDT",
                    "amount", amount,
                    "txId", txId,
                    "creditedAt", creditedAt
                ))
                .when()
                .post("/account/api/account/deposits")
                .then()
                .statusCode(200);
        }

        private Map<String, Object> findInstrument(String token, int instrumentId) {
            Response response = given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/admin/api/admin/instruments");
            response.then().statusCode(200);
            List<Map<String, Object>> instruments = response.jsonPath().getList("data");
            Map<String, Object> instrument = instruments.stream()
                .filter(item -> instrumentId == parseInt(item.get("instrumentId")))
                .findFirst()
                .orElse(null);
            assertNotNull(instrument, "Instrument not found");
            return instrument;
        }

        private Map<String, Object> getRiskLimit(String token, int instrumentId) {
            Response response = given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/risk/api/risk/limits/" + instrumentId);
            response.then().statusCode(200);
            Map<String, Object> risk = response.jsonPath().getMap("data");
            assertNotNull(risk, "Risk limit not found");
            return risk;
        }

        private void placeOrder(String token, int instrumentId, OrderSide side, double price, int quantity) {
            given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of(
                    "instrumentId", instrumentId,
                    "side", side.name(),
                    "type", "LIMIT",
                    "price", price,
                    "quantity", quantity,
                    "clientOrderId", UUID.randomUUID().toString()
                ))
                .when()
                .post("/order/api/orders")
                .then()
                .statusCode(200);
        }

        private Map<String, Object> findPosition(String token, int instrumentId) {
            Response response = given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/position/api/positions?instrumentId=" + instrumentId);
            response.then().statusCode(200);
            List<Map<String, Object>> positions = response.jsonPath().getList("data");
            if (positions == null) {
                return null;
            }
            return positions.stream()
                .filter(item -> instrumentId == parseInt(item.get("instrumentId")))
                .findFirst()
                .orElse(null);
        }

        private Map<String, Object> getBalanceSheet(String token) {
            Response response = given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/account/api/account/balance-sheet");
            response.then().statusCode(200);
            Map<String, Object> data = response.jsonPath().getMap("data");
            assertNotNull(data, "Balance sheet missing");
            return data;
        }
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
                return new ExpectedPosition("CLOSED",
                    side == PositionSide.LONG ? "LONG" : "SHORT",
                    BigDecimal.ZERO,
                    entryPrice,
                    BigDecimal.ZERO,
                    BigDecimal.valueOf(markPrice),
                    cumRealizedPnl,
                    cumFee,
                    cumFundingFee);
            }
            return new ExpectedPosition("ACTIVE",
                side == PositionSide.LONG ? "LONG" : "SHORT",
                quantity,
                entryPrice,
                BigDecimal.ZERO,
                BigDecimal.valueOf(markPrice),
                cumRealizedPnl,
                cumFee,
                cumFundingFee);
        }
    }

    private record ExpectedPosition(String status,
                                    String side,
                                    BigDecimal quantity,
                                    BigDecimal entryPrice,
                                    BigDecimal closingReservedQuantity,
                                    BigDecimal markPrice,
                                    BigDecimal cumRealizedPnl,
                                    BigDecimal cumFee,
                                    BigDecimal cumFundingFee) {
    }
}
