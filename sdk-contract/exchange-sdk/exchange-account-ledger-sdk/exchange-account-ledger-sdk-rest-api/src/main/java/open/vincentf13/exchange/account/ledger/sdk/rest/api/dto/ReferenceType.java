package open.vincentf13.exchange.account.ledger.sdk.rest.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ReferenceType {
    DEPOSIT,
    WITHDRAWAL;

    @JsonValue
    public String code() {
        return name();
    }
}
