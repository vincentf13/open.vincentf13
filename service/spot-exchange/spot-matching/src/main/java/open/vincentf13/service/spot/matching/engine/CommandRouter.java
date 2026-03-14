package open.vincentf13.service.spot.matching.engine;

import lombok.RequiredArgsConstructor;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.model.WalProgress;
import open.vincentf13.service.spot.model.command.*;
import open.vincentf13.service.spot.sbe.OrderCreateDecoder;
import org.springframework.stereotype.Component;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static open.vincentf13.service.spot.infra.Constants.*;

/**
 * 指令路由處理器 (Command Router)
 * 職責：解析 WAL 數據 (SBE 解碼) 並分發給對應的業務 Processor 處理
 */
@Component
@RequiredArgsConstructor
public class CommandRouter {
    private final OrderProcessor orderProcessor;
    private final AuthProcessor authProcessor;
    private final DepositProcessor depositProcessor;

    public long route(int msgType, net.openhft.chronicle.wire.WireIn wire, WalProgress progress, 
                      boolean isReplaying, Supplier<Long> orderIdSupplier, LongSupplier tradeIdSupplier) {
        
        return switch (msgType) {
            case MsgType.AUTH         -> handleAuth(wire);
            case MsgType.ORDER_CREATE  -> handleOrderCreate(wire, orderIdSupplier, tradeIdSupplier);
            case MsgType.ORDER_CANCEL  -> handleOrderCancel(wire);
            case MsgType.DEPOSIT      -> handleDeposit(wire);
            default -> MSG_SEQ_NONE;
        };
    }

    private long handleAuth(net.openhft.chronicle.wire.WireIn wire) {
        ThreadContext context = ThreadContext.get();
        AuthCommand cmd = context.getAuthCommand();
        wire.read(ChronicleWireKey.payload).bytes(cmd);
        
        context.setCurrentGatewaySequence(cmd.getSeq());
        authProcessor.handleAuth(cmd.getUserId(), cmd.getSeq());
        
        return cmd.getSeq();
    }

    private long handleOrderCreate(net.openhft.chronicle.wire.WireIn wire, Supplier<Long> orderIdSupplier, LongSupplier tradeIdSupplier) {
        ThreadContext context = ThreadContext.get();
        OrderCreateCommand cmd = context.getOrderCreateCommand();
        wire.read(ChronicleWireKey.payload).bytes(cmd);
        
        context.setCurrentGatewaySequence(cmd.getSeq());
        orderProcessor.processCreateCommand(cmd.getUserId(), cmd.getSymbolId(), cmd.getPrice(), cmd.getQty(), cmd.getSide(), cmd.getClientOrderId(), cmd.getSeq(), orderIdSupplier, tradeIdSupplier);
        
        return cmd.getSeq();
    }

    private long handleOrderCancel(net.openhft.chronicle.wire.WireIn wire) {
        ThreadContext context = ThreadContext.get();
        OrderCancelCommand cmd = context.getOrderCancelCommand();
        wire.read(ChronicleWireKey.payload).bytes(cmd);
        
        context.setCurrentGatewaySequence(cmd.getSeq());
        orderProcessor.processCancelCommand(cmd.getUserId(), cmd.getOrderId(), cmd.getSeq());
        
        return cmd.getSeq();
    }

    private long handleDeposit(net.openhft.chronicle.wire.WireIn wire) {
        ThreadContext context = ThreadContext.get();
        DepositCommand cmd = context.getDepositCommand();
        wire.read(ChronicleWireKey.payload).bytes(cmd);
        
        context.setCurrentGatewaySequence(cmd.getSeq());
        depositProcessor.handleDeposit(cmd.getUserId(), cmd.getAssetId(), cmd.getAmount(), cmd.getSeq());
        
        return cmd.getSeq();
    }
}
