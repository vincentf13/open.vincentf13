package open.vincentf13.service.spot.matching.engine;

import lombok.RequiredArgsConstructor;
import open.vincentf13.service.spot.infra.alloc.SbeCodec;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.model.WalProgress;
import open.vincentf13.service.spot.model.command.*;
import org.springframework.stereotype.Component;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static open.vincentf13.service.spot.infra.Constants.*;

/**
 * 指令路由處理器 (Command Router)
 * 職責：解析 WAL 數據並分發給對應的業務 Processor 處理
 */
@Component
@RequiredArgsConstructor
public class CommandRouter {
    private final OrderProcessor orderProcessor;
    private final AuthProcessor authProcessor;
    private final DepositProcessor depositProcessor;
    private final SnapshotService snapshotService;

    /** 
     * 指令分發入口
     * @return 返回當前處理指令的 Gateway 序號 (gwSeq)
     */
    public long route(int msgType, net.openhft.chronicle.wire.WireIn wire, WalProgress progress, 
                      boolean isReplaying, Supplier<Long> orderIdSupplier, LongSupplier tradeIdSupplier) {
        
        return switch (msgType) {
            case MsgType.AUTH         -> handleAuth(wire);
            case MsgType.ORDER_CREATE  -> handleOrderCreate(wire, orderIdSupplier, tradeIdSupplier);
            case MsgType.ORDER_CANCEL  -> handleOrderCancel(wire);
            case MsgType.DEPOSIT      -> handleDeposit(wire);
            case MsgType.SNAPSHOT     -> handleSnapshot(wire, progress, isReplaying);
            default -> MSG_SEQ_NONE;
        };
    }

    private long handleAuth(net.openhft.chronicle.wire.WireIn wire) {
        AuthCommand cmd = ThreadContext.get().getAuthCommand();
        wire.read(ChronicleWireKey.payload).bytes(cmd);
        authProcessor.handleAuth(SbeCodec.decodeAuth(cmd.getPointBytesStore()).userId(), cmd.getSeq());
        return cmd.getSeq();
    }

    private long handleOrderCreate(net.openhft.chronicle.wire.WireIn wire, Supplier<Long> orderIdSupplier, LongSupplier tradeIdSupplier) {
        OrderCreateCommand cmd = ThreadContext.get().getOrderCreateCommand();
        wire.read(ChronicleWireKey.payload).bytes(cmd);
        orderProcessor.processCreateCommand(cmd.getPointBytesStore(), cmd.getSeq(), orderIdSupplier, tradeIdSupplier);
        return cmd.getSeq();
    }

    private long handleOrderCancel(net.openhft.chronicle.wire.WireIn wire) {
        OrderCancelCommand cmd = ThreadContext.get().getOrderCancelCommand();
        wire.read(ChronicleWireKey.payload).bytes(cmd);
        var decoder = SbeCodec.decodeOrderCancel(cmd.getPointBytesStore());
        orderProcessor.processCancelCommand(decoder.userId(), decoder.orderId(), cmd.getSeq());
        return cmd.getSeq();
    }

    private long handleDeposit(net.openhft.chronicle.wire.WireIn wire) {
        DepositCommand cmd = ThreadContext.get().getDepositCommand();
        wire.read(ChronicleWireKey.payload).bytes(cmd);
        var decoder = SbeCodec.decodeDeposit(cmd.getPointBytesStore());
        depositProcessor.handleDeposit(decoder.userId(), decoder.assetId(), decoder.amount(), cmd.getSeq());
        return cmd.getSeq();
    }

    private long handleSnapshot(net.openhft.chronicle.wire.WireIn wire, WalProgress progress, boolean isReplaying) {
        SnapshotCommand cmd = ThreadContext.get().getSnapshotCommand();
        wire.read(ChronicleWireKey.payload).bytes(cmd);
        if (!isReplaying) {
            snapshotService.createSnapshot(progress);
        }
        return cmd.getSeq();
    }
}
