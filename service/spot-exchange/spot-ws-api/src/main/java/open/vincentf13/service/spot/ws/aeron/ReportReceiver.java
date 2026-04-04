package open.vincentf13.service.spot.ws.aeron;

import io.aeron.Aeron;
import io.aeron.FragmentAssembler;
import io.aeron.Subscription;
import io.aeron.logbuffer.Header;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.aeron.AeronConstants;
import open.vincentf13.service.spot.infra.aeron.AeronUtil;
import open.vincentf13.service.spot.infra.thread.Worker;
import open.vincentf13.service.spot.ws.ws.WsSessionManager;
import org.agrona.DirectBuffer;
import org.springframework.stereotype.Component;

import java.nio.ByteOrder;

import static open.vincentf13.service.spot.infra.Constants.*;

/**
 * 回報接收器 (Report Receiver)
 *
 * ���閱 Matching Engine 的 Aeron 回報流，
 * 將 SBE 編碼的執行回報原樣轉發至對應用��的 WebSocket 連��。
 *
 * 回報格式：[0-3] MsgType | [4-11] reserved | [12-19] SBE header | [20+] SBE body
 * userId 位於 SBE body 的第二個欄位 (offset 20+8=28)。
 */
@Slf4j
@Component
public class ReportReceiver extends Worker {

    private static final int USER_ID_OFFSET = 28; // 20 (header) + 8 (timestamp) = userId position

    private final WsSessionManager sessionManager;
    private Subscription subscription;
    private FragmentAssembler assembler;

    /** @param aeron 僅用於建立 Spring Bean 依賴順序 */
    public ReportReceiver(@SuppressWarnings("unused") Aeron aeron, WsSessionManager sessionManager) {
        super("report-receiver",
              MetricsKey.CPU_ID_WAL_WRITER, MetricsKey.CPU_ID_CURRENT_WAL_WRITER,
              MetricsKey.GATEWAY_WAL_WRITER_DUTY_CYCLE);
        this.sessionManager = sessionManager;
    }

    @PostConstruct @Override public void start() { super.start(); }
    @PreDestroy @Override public void stop() { super.stop(); }

    @Override
    protected void onStart() {
        subscription = AeronUtil.aeron().addSubscription(AeronChannel.REPORT_FLOW, AeronChannel.REPORT_STREAM_ID);
        assembler = new FragmentAssembler(this::onReport);
        log.info("ReportReceiver 已啟動，訂閱回報流");
    }

    @Override
    protected int doWork() {
        return subscription.poll(assembler, AeronConstants.AERON_POLL_LIMIT);
    }

    private void onReport(DirectBuffer buffer, int offset, int length, Header header) {
        if (length < USER_ID_OFFSET + 8) return;
        long userId = buffer.getLong(offset + USER_ID_OFFSET, ByteOrder.LITTLE_ENDIAN);

        // 從 Aeron DirectBuffer 拷貝至 Netty ByteBuf，推送至 WebSocket
        byte[] bytes = new byte[length];
        buffer.getBytes(offset, bytes, 0, length);
        sessionManager.sendMessage(userId, Unpooled.wrappedBuffer(bytes));
    }

    @Override
    protected void onStop() {
        if (subscription != null) subscription.close();
    }
}
