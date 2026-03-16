package open.vincentf13.service.spot.matching.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.model.command.*;
import org.springframework.stereotype.Component;

import org.agrona.DirectBuffer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static open.vincentf13.service.spot.infra.Constants.*;
import static open.vincentf13.service.spot.infra.alloc.OffHeapUtil.getAddress;

/** 
 指令路由器 (RingBuffer 專用版)
 徹底移除對 Chronicle Wire 的依賴，直接在 DirectBuffer 上進行 SBE 解碼分發。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandRouter {
    private final OrderProcessor orderProcessor;
    private final AuthProcessor authProcessor;
    private final DepositProcessor depositProcessor;

    /** 從 RingBuffer 讀取並分發：直接處理 DirectBuffer */
    public long route(int msgType, DirectBuffer buffer, int offset, int length, Supplier<Long> orderIdSupplier, LongSupplier tradeIdSupplier) {
        if (length <= 0) return MSG_SEQ_NONE;

        final long address = getAddress(buffer, offset);
        final ThreadContext ctx = ThreadContext.get();

        return switch (msgType) {
            case MsgType.AUTH -> {
                AuthCommand cmd = ctx.getAuthCommand();
                cmd.wrap(address, length);
                authProcessor.handleAuth(cmd.getUserId(), cmd.getSeq());
                yield cmd.getSeq();
            }
            case MsgType.ORDER_CREATE -> {
                OrderCreateCommand cmd = ctx.getOrderCreateCommand();
                cmd.wrap(address, length);
                orderProcessor.processCreateCommand(cmd.getUserId(), cmd.getSymbolId(), cmd.getPrice(), cmd.getQty(), cmd.getSide(), cmd.getClientOrderId(), cmd.getSeq(), orderIdSupplier, tradeIdSupplier);
                yield cmd.getSeq();
            }
            case MsgType.ORDER_CANCEL -> {
                OrderCancelCommand cmd = ctx.getOrderCancelCommand();
                cmd.wrap(address, length);
                orderProcessor.processCancelCommand(cmd.getUserId(), cmd.getOrderId(), cmd.getSeq());
                yield cmd.getSeq();
            }
            case MsgType.DEPOSIT -> {
                DepositCommand cmd = ctx.getDepositCommand();
                cmd.wrap(address, length);
                depositProcessor.handleDeposit(cmd.getUserId(), cmd.getAssetId(), cmd.getAmount(), cmd.getSeq());
                yield cmd.getSeq();
            }
            default -> {
                log.warn("[ROUTER] 收到未知訊息類型: {}, len={}", msgType, length);
                yield MSG_SEQ_NONE;
            }
        };
    }
}
