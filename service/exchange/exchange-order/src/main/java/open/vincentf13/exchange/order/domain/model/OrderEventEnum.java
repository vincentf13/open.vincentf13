package open.vincentf13.exchange.order.domain.model;

import lombok.Getter;
import open.vincentf13.sdk.auth.jwt.OpenJwtLoginUserInfo;

@Getter
public enum OrderEventEnum {
    ORDER_CREATE_API("OrderApi.create", "", Actor.USER),
    POSITION_RESERVED("PositionReserved", "", Actor.POSITION_SERVICE),
    POSITION_RESERVE_REJECTED("PositionReserveRejected", "", Actor.POSITION_SERVICE),
    MARGIN_PRECHECK_FAILED("MarginPreCheckFailed", "", Actor.RISK_SERVICE),
    FUNDS_FROZEN("FundsFrozen", "", Actor.LEDGER_SERVICE),
    FUNDS_FREEZE_FAILED("FundsFreezeFailed", "", Actor.LEDGER_SERVICE),
    FUNDS_UNFROZEN("FundsUnfrozen", "", Actor.LEDGER_SERVICE),
    TRADE_EXECUTED("TradeExecuted", "", Actor.MATCHING_ENGINE),
    ORDER_CANCEL_API("OrderApi.cancel", "", Actor.USER),
    ORDER_CANCEL_REQUESTED("OrderCancelRequested", "", Actor.MATCHING_ENGINE);

    private final String eventType;
    private final String referenceType;
    private final Actor actor;

    public String resolveActor() {
        if (actor == Actor.USER) {
            Long currentUserId = OpenJwtLoginUserInfo.currentUserId();
            return currentUserId != null ? "USER:" + currentUserId : "USER:UNKNOWN";
        }
        return actor.getLabel();
    }


    OrderEventEnum(String eventType, String referenceType, Actor actor) {
        this.eventType = eventType;
        this.referenceType = referenceType;
        this.actor = actor;
    }

    @Getter
    public enum Actor {
        USER("USER"),
        POSITION_SERVICE("POSITION_SERVICE"),
        RISK_SERVICE("RISK_SERVICE"),
        LEDGER_SERVICE("LEDGER_SERVICE"),
        MATCHING_ENGINE("MATCHING_ENGINE"),
        ORDER_SERVICE("ORDER_SERVICE"),
        SYSTEM("SYSTEM");

        private final String label;

        Actor(String label) {
            this.label = label;
        }
    }
}
