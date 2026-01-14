package open.vincentf13.exchange.test;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import open.vincentf13.exchange.account.sdk.rest.api.dto.AccountBalanceSheetResponse;
import open.vincentf13.exchange.admin.contract.dto.InstrumentDetailResponse;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionResponse;
import open.vincentf13.exchange.risk.sdk.rest.api.RiskLimitResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApiClient {
    ApiClient(String baseUri) {
        RestAssured.baseURI = baseUri;
    }

    void resetData() {
        given()
            .when()
            .post("/admin/api/admin/system/reset-data")
            .then()
            .statusCode(200);
    }

    String login(String email, String password) {
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

    void deposit(String token, double amount) {
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

    InstrumentDetailResponse findInstrument(String token, int instrumentId) {
        Response response = given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/admin/api/admin/instruments");
        response.then().statusCode(200);
        List<InstrumentDetailResponse> instruments = response.jsonPath().getList("data", InstrumentDetailResponse.class);
        InstrumentDetailResponse instrument = instruments.stream()
            .filter(item -> item.instrumentId() != null && item.instrumentId().intValue() == instrumentId)
            .findFirst()
            .orElse(null);
        assertNotNull(instrument, "Instrument not found");
        return instrument;
    }

    RiskLimitResponse getRiskLimit(String token, int instrumentId) {
        Response response = given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/risk/api/risk/limits/" + instrumentId);
        response.then().statusCode(200);
        RiskLimitResponse risk = response.jsonPath().getObject("data", RiskLimitResponse.class);
        assertNotNull(risk, "Risk limit not found");
        return risk;
    }

    void placeOrder(String token, int instrumentId, OrderSide side, double price, int quantity) {
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

    PositionResponse findPosition(String token, int instrumentId) {
        Response response = given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/position/api/positions?instrumentId=" + instrumentId);
        response.then().statusCode(200);
        List<PositionResponse> positions = response.jsonPath().getList("data", PositionResponse.class);
        if (positions == null) {
            return null;
        }
        return positions.stream()
            .filter(item -> item.instrumentId() != null && item.instrumentId().intValue() == instrumentId)
            .findFirst()
            .orElse(null);
    }

    AccountBalanceSheetResponse getBalanceSheet(String token) {
        Response response = given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/account/api/account/balance-sheet");
        response.then().statusCode(200);
        AccountBalanceSheetResponse data = response.jsonPath().getObject("data", AccountBalanceSheetResponse.class);
        assertNotNull(data, "Balance sheet missing");
        return data;
    }
}
