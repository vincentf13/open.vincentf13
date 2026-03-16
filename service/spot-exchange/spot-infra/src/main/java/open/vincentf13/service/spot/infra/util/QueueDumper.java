package open.vincentf13.service.spot.infra.util;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.DocumentContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.command.AbstractSbeModel;

/**
 * WAL 數據分析工具
 */
public class QueueDumper {
    public static void main(String[] args) {
        // 設定絕對路徑，確保讀取正確的目錄
        System.setProperty("SPOT_WAL_DIR", "C:/iProject/open.vincentf13/data/spot-exchange/wal/");
        
        try (ChronicleQueue queue = Storage.self().gatewaySenderWal()) {
            ExcerptTailer tailer = queue.createTailer();
            System.out.println("--- 開始傾倒 Gateway WAL 內容 ---");
            int count = 0;
            while (true) {
                try (DocumentContext dc = tailer.readingDocument()) {
                    if (!dc.isPresent()) break;
                    
                    Bytes<?> bytes = dc.wire().bytes();
                    if (bytes.readRemaining() < 16) {
                        System.err.printf("Index: %d [Invalid - Length too short: %d]%n", dc.index(), bytes.readRemaining());
                        continue;
                    }

                    int len = bytes.readInt();
                    int msgType = bytes.readInt();
                    long seq = bytes.readLong();
                    
                    System.out.printf("Index: %d, MsgType: %d, Seq: %d, Length: %d%n", 
                        dc.index(), msgType, seq, len);
                    bytes.readSkip((long) len - 12);
                    count++;
                }
            }
            System.out.println("--- 傾倒結束，共計 " + count + " 筆訊息 ---");
        }
    }
}
