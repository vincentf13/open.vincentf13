package open.vincentf13.service.spot.matching.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.MappedBytes;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.WalProgress;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 系統快照服務 (Snapshot Service)
 職責：異步備份 Chronicle Map 文件，獨佔一個 CPU 核心以實現零停頓快照
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotService extends Worker {
    private final String snapshotDir = ChronicleMapEnum.SNAPSHOT_BASE_DIR;
    private static final String BOOK_SNAPSHOT_FILE = "order-book.bin";
    
    private final BlockingQueue<WalProgress> snapshotRequests = new LinkedBlockingQueue<>();

    @PostConstruct
    public void init() {
        start("snapshot-worker");
    }

    @Override
    protected void onBind(int cpuId) {
        Storage.self().metricsHistory().put(Storage.KEY_CPU_ID_SNAPSHOT, (long) cpuId);
    }

    @Override
    protected void onStart() {
        log.info("Snapshot Service Worker started, awaiting requests...");
    }

    @Override
    protected int doWork() {
        WalProgress progress = snapshotRequests.poll();
        if (progress != null) {
            executeCreateSnapshot(progress);
            return 1;
        }
        return 0;
    }

    @Override
    protected void onStop() {
        log.info("Snapshot Service Worker stopped");
    }

    public void requestSnapshot(WalProgress currentProgress) {
        // 僅保留最新的快照請求，避免堆積
        if (snapshotRequests.size() > 1) snapshotRequests.clear();
        snapshotRequests.offer(currentProgress);
    }

    private void executeCreateSnapshot(WalProgress currentProgress) {
        long seq = currentProgress.getLastProcessedMsgSeq();
        log.debug("--- 開始執行系統快照 (Sequence: {}) ---", seq);
        
        File finalDir = new File(snapshotDir + seq);
        File tmpDir = new File(snapshotDir + seq + "_tmp");
        
        try {
            if (tmpDir.exists()) deleteDir(tmpDir);
            if (!tmpDir.mkdirs()) throw new IOException("無法創建臨時快照目錄: " + tmpDir.getAbsolutePath());

            // 1. 備份元數據
            Storage.self().walMetadata().put(MetaDataKey.Wal.LAST_SNAPSHOT_INFO, currentProgress);
            
            // 2. 備份 Chronicle Map 文件至臨時目錄
            backupMap(ChronicleMapEnum.BALANCES, tmpDir);
            backupMap(ChronicleMapEnum.ORDERS, tmpDir);
            backupMap(ChronicleMapEnum.USER_ASSETS, tmpDir);
            backupMap(ChronicleMapEnum.USER_ACTIVE_ORDERS, tmpDir);
            backupMap(ChronicleMapEnum.ACTIVE_ORDERS, tmpDir);
            backupMap("msg-" + ChronicleMapEnum.METADATA, tmpDir);
            backupMap("wal-" + ChronicleMapEnum.METADATA, tmpDir);

            // 3. 備份內存 OrderBook 狀態至臨時目錄
            saveOrderBookSnapshot(new File(tmpDir, BOOK_SNAPSHOT_FILE));

            // 4. 重命名目錄 (Windows 上 ATOMIC_MOVE 有時會失敗)
            if (finalDir.exists()) deleteDir(finalDir);
            try {
                Files.move(tmpDir.toPath(), finalDir.toPath());
            } catch (IOException e) {
                log.warn("目錄移動失敗，嘗試拷貝並刪除: {}", e.getMessage());
                copyDir(tmpDir, finalDir);
                deleteDir(tmpDir);
            }

            log.debug("✅ 快照創建成功，存儲路徑: {}", finalDir.getAbsolutePath());
            cleanupOldSnapshots(finalDir.getParentFile());

        } catch (Exception e) {
            log.error("❌ 快照創建失敗: {}", e.getMessage(), e);
            if (tmpDir.exists()) deleteDir(tmpDir);
        }
    }

    private void copyDir(File src, File dst) throws IOException {
        if (!dst.exists()) dst.mkdirs();
        File[] files = src.listFiles();
        if (files != null) {
            for (File f : files) {
                File target = new File(dst, f.getName());
                if (f.isDirectory()) {
                    copyDir(f, target);
                } else {
                    Files.copy(f.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void saveOrderBookSnapshot(File file) throws IOException {
        // 預估 128MB 緩衝區 (可根據訂單量動態調整)
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
        
        if (src.exists()) {
            Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } else {
            log.warn("無法備份不存在的 Map 文件: {}", src.getAbsolutePath());
        }
    }

    private void cleanupOldSnapshots(File parent) {
        File[] snapshots = parent.listFiles(File::isDirectory);
        if (snapshots != null && snapshots.length > 3) {
            // 依據目錄名 (Sequence) 排序
            java.util.Arrays.sort(snapshots, (a, b) -> {
                try {
                    return Long.compare(Long.parseLong(a.getName()), Long.parseLong(b.getName()));
                } catch (NumberFormatException e) {
                    return 0;
                }
            });
            for (int i = 0; i < snapshots.length - 3; i++) {
                deleteDir(snapshots[i]);
                log.info("清理過期快照: {}", snapshots[i].getName());
            }
        }
    }

    private void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDir(f);
                else f.delete();
            }
        }
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
