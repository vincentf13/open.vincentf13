package open.vincentf13.service.spot.model.command;

import open.vincentf13.service.spot.sbe.OrderStatus;

/** 執行回報抽象接口 (用於 WebSocket 推送) */
public interface AbstractExecutionReport {
    long getUserId();
    long getOrderId();
    OrderStatus getStatus();
    long getClientOrderId();
    long getTimestamp();
}
