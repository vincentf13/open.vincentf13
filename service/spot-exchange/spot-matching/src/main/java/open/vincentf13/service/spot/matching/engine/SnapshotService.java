package open.vincentf13.service.spot.matching.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.bytes.MappedBytes;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.WalProgress;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 系統快照服務 (Snapshot Service)
 職責：定期備份 Chronicle Map 文件，並保存一致性的進度位點，確保秒級冷啟動
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotService {
    private final String snapshotDir = ChronicleMapEnum.SNAPSHOT_BASE_DIR;
    private static final String BOOK_SNAPSHOT_FILE = "order-book.bin";

    public void createSnapshot(WalProgress currentProgress) {
        long seq = currentProgress.getLastProcessedMsgSeq();
        log.info("--- 開始執行系統快照 (Sequence: {}) ---", seq);
        
        try {
            File dir = new File(snapshotDir + seq);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("無法創建快照目錄: " + dir.getAbsolutePath());
            }

            // 1. 備份元數據
            Storage.self().walMetadata().put(MetaDataKey.Wal.LAST_SNAPSHOT_INFO, currentProgress);
            
            // 2. 備份 Chronicle Map 文件
            backupMap(ChronicleMapEnum.BALANCES, dir);
            backupMap(ChronicleMapEnum.ORDERS, dir);
            backupMap(ChronicleMapEnum.USER_ASSETS, dir);
            backupMap(ChronicleMapEnum.USER_ACTIVE_ORDERS, dir);
            backupMap(ChronicleMapEnum.ACTIVE_ORDERS, dir);
            backupMap("msg-" + ChronicleMapEnum.METADATA, dir);
            backupMap("wal-" + ChronicleMapEnum.METADATA, dir);

            // 3. 備份內存 OrderBook 狀態 (二進制快照)
            saveOrderBookSnapshot(new File(dir, BOOK_SNAPSHOT_FILE));

            log.info("✅ 快照創建成功，存儲路徑: {}", dir.getAbsolutePath());
            
            cleanupOldSnapshots(dir.getParentFile());

        } catch (Exception e) {
            log.error("❌ 快照創建失敗: {}", e.getMessage(), e);
        }
    }

    private void saveOrderBookSnapshot(File file) throws IOException {
        // 預估 100MB 緩衝區 (可根據訂單量動態調整)
        try (MappedBytes bytes = MappedBytes.mappedBytes(file, 128 * 1024 * 1024)) {
            bytes.writeInt(OrderBook.getInstances().size());
            for (OrderBook book : OrderBook.getInstances()) {
                book.saveState(bytes);
            }
        }
    }

    private void loadOrderBookSnapshot(File file) throws IOException {
        if (!file.exists()) return;
        try (MappedBytes bytes = MappedBytes.mappedBytes(file, file.length())) {
            int bookCount = bytes.readInt();
            for (int i = 0; i < bookCount; i++) {
                // 這裡需要先 readInt 出 symbolId 以定位實例
                long pos = bytes.readPosition();
                int symbolId = bytes.readInt();
                bytes.readPosition(pos); // 回退，讓 loadState 讀取完整結構
                OrderBook.get(symbolId).loadState(bytes);
            }
        }
    }

    private void backupMap(String mapName, File targetDir) throws IOException {
        File src = new File(ChronicleMapEnum.DEFAULT_BASE_DIR + mapName);
        File dst = new File(targetDir, mapName);
        
        // 核心：使用 Chronicle Map 的持久化機制 (雖然 Map 正在使用，但 copyTo 會盡力保證數據一致性)
        // 註：在極端併發下，配合 Engine 暫停 (Barrier) 會更安全
        Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void cleanupOldSnapshots(File parent) {
        File[] snapshots = parent.listFiles(File::isDirectory);
        if (snapshots != null && snapshots.length > 3) {
            // 依據目錄名 (Sequence) 排序
            java.util.Arrays.sort(snapshots, (a, b) -> Long.compare(Long.parseLong(a.getName()), Long.parseLong(b.getName())));
            for (int i = 0; i < snapshots.length - 3; i++) {
                deleteDir(snapshots[i]);
                log.info("清理過期快照: {}", snapshots[i].getName());
            }
        }
    }

    private void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) f.delete();
        dir.delete();
    }
    
    /** 
      從快照恢復：將備份文件覆蓋回工作目錄 
     */
    public boolean recoverFromLatestSnapshot() {
        File parent = new File(snapshotDir);
        if (!parent.exists()) return false;
        
        File[] snapshots = parent.listFiles(File::isDirectory);
        if (snapshots == null || snapshots.length == 0) return false;
        
        // 取得最新的序號目錄
        java.util.Arrays.sort(snapshots, (a, b) -> Long.compare(Long.parseLong(b.getName()), Long.parseLong(a.getName())));
        File latest = snapshots[0];
        
        log.info("🔄 檢測到系統快照，正在從 {} 恢復狀態...", latest.getName());
        
        try {
            File[] files = latest.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (BOOK_SNAPSHOT_FILE.equals(f.getName())) {
                        loadOrderBookSnapshot(f);
                    } else {
                        Files.copy(f.toPath(), new File(ChronicleMapEnum.DEFAULT_BASE_DIR + f.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            return true;
        } catch (IOException e) {
            log.error("❌ 從快照恢復失敗: {}", e.getMessage());
            return false;
        }
    }
}
