package open.vincentf13.exchange.position.service;

import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentRequest;

import java.math.BigDecimal;

public final class PositionIntentValidator {

    private PositionIntentValidator() {
    }

    public static void validate(PositionIntentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("PositionIntentRequest must not be null");
        }
        if (request.userId() == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (request.instrumentId() == null) {
            throw new IllegalArgumentException("instrumentId is required");
        }
        if (request.orderSide() == null) {
            throw new IllegalArgumentException("orderSide is required");
        }
        if (request.quantity() == null) {
            throw new IllegalArgumentException("quantity is required");
        }
        if (request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
