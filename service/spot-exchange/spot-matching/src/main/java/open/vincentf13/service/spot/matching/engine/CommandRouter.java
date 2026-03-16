package open.vincentf13.service.spot.matching.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.model.command.*;
import org.springframework.stereotype.Component;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static open.vincentf13.service.spot.infra.Constants.*;
import static org.agrona.UnsafeAccess.UNSAFE;

/** 
 指令路由器 (Raw WAL 版)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandRouter {
    private final OrderProcessor orderProcessor;
    private final AuthProcessor authProcessor;
    private final DepositProcessor depositProcessor;

    /** 核心路由入口：從原始字節流讀取並分發 */
    public long routeRaw(net.openhft.chronicle.wire.WireIn wire, Supplier<Long> orderIdSupplier, LongSupplier tradeIdSupplier) {
        final net.openhft.chronicle.bytes.Bytes<?> bytes = wire.bytes();
        if (bytes.readRemaining() < 4) return MSG_SEQ_NONE;
        
        int payloadLen = bytes.readInt();
        if (bytes.readRemaining() < payloadLen) return MSG_SEQ_NONE;

        final long addressForRead = bytes.addressForRead(bytes.readPosition());
        final int msgType = bytes.readInt(); // 改用 readInt()，它會從當前 position (Length之後) 讀取 4 字節
        
        final ThreadContext ctx = ThreadContext.get();

        return switch (msgType) {
            case MsgType.AUTH -> {
                AuthCommand cmd = ctx.getAuthCommand();
                cmd.wrap(addressForRead, (long) payloadLen);
                authProcessor.handleAuth(cmd.getUserId(), cmd.getSeq());
                bytes.readSkip((long) payloadLen - 4); // 扣除已經 readInt 掉的 4 字節
                yield cmd.getSeq();
            }
            case MsgType.ORDER_CREATE -> {
                OrderCreateCommand cmd = ctx.getOrderCreateCommand();
                cmd.wrap(addressForRead, (long) payloadLen);
                orderProcessor.processCreateCommand(cmd.getUserId(), cmd.getSymbolId(), cmd.getPrice(), cmd.getQty(), cmd.getSide(), cmd.getClientOrderId(), cmd.getSeq(), orderIdSupplier, tradeIdSupplier);
                bytes.readSkip((long) payloadLen - 4);
                yield cmd.getSeq();
            }
            case MsgType.ORDER_CANCEL -> {
                OrderCancelCommand cmd = ctx.getOrderCancelCommand();
                cmd.wrap(addressForRead, (long) payloadLen);
                orderProcessor.processCancelCommand(cmd.getUserId(), cmd.getOrderId(), cmd.getSeq());
                bytes.readSkip((long) payloadLen - 4);
                yield cmd.getSeq();
            }
            case MsgType.DEPOSIT -> {
                DepositCommand cmd = ctx.getDepositCommand();
                cmd.wrap(addressForRead, (long) payloadLen);
                depositProcessor.handleDeposit(cmd.getUserId(), cmd.getAssetId(), cmd.getAmount(), cmd.getSeq());
                bytes.readSkip((long) payloadLen - 4);
                yield cmd.getSeq();
            }
            default -> {
                log.warn("[ROUTER] 收到未知訊息類型: {}, len={}", msgType, payloadLen);
                bytes.readSkip((long) payloadLen);
                yield MSG_SEQ_NONE;
            }
        };
    }
}
