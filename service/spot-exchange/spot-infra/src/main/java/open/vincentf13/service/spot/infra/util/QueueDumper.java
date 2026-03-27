package open.vincentf13.service.spot.infra.util;

import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.DocumentContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;

/**
 * WAL 數據分析工具
 */
@Slf4j
public class QueueDumper {
    public static void main(String[] args) {
        // 設定絕對路徑，確保讀取正確的目錄
        System.setProperty("SPOT_WAL_DIR", "C:/iProject/open.vincentf13/data/spot-exchange/wal/");
        
        try (ChronicleQueue queue = Storage.self().openGatewaySenderWal()) {
            ExcerptTailer tailer = queue.createTailer();
            log.info("--- 開始傾倒 Gateway WAL 內容 ---");
            int count = 0;
            while (true) {
                try (DocumentContext dc = tailer.readingDocument()) {
                    if (!dc.isPresent()) break;
                    
                    Bytes<?> bytes = dc.wire().bytes();
                    if (bytes.readRemaining() < 16) {
                        log.warn("Index: {} [Invalid - Length too short: {}]", dc.index(), bytes.readRemaining());
                        continue;
                    }

                    int len = bytes.readInt();
                    int msgType = bytes.readInt();
                    long seq = bytes.readLong();
                    
                    log.info("Index: {}, MsgType: {}, Seq: {}, Length: {}", dc.index(), msgType, seq, len);
                    bytes.readSkip((long) len - 12);
                    count++;
                }
            }
            log.info("--- 傾倒結束，共計 {} 筆訊息 ---", count);
        }
    }
}
