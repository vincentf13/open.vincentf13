package open.vincentf13.service.spot.infra.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

/**
 * 記憶體頁面預熱工具 (Pre-touch Utility)
 *
 * 讀取 mmap 文件每一頁（4KB），觸發 minor page fault 讓 OS 為 virtual page
 * 分配 physical page，消除運行時首次存取的 fault 成本。
 * 實際駐留靠關閉 swap + 充足 RAM，不是靠 mlock。
 */
@Slf4j
public class PreTouchUtil {

    private static final int PAGE_SIZE = 4096;
    private static final long MAX_MAP_CHUNK = 1L << 30; // 1GB per mmap chunk

    /**
     * 預熱單一文件：讀取每一頁以觸發實體頁面分配。
     * @return 已觸碰的頁數
     */
    public static long touchFile(File file) {
        if (!file.isFile() || file.length() == 0) return 0;
        long size = file.length();
        long pages = 0;
        try (FileChannel ch = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            long offset = 0;
            while (offset < size) {
                long chunkSize = Math.min(size - offset, MAX_MAP_CHUNK);
                MappedByteBuffer buf = ch.map(FileChannel.MapMode.READ_ONLY, offset, chunkSize);
                for (int i = 0; i < (int) chunkSize; i += PAGE_SIZE) {
                    buf.get(i);
                    pages++;
                }
                offset += chunkSize;
            }
        } catch (IOException e) {
            log.warn("[PRE-TOUCH] 文件預熱失敗: {} ({})", file.getName(), e.getMessage());
        }
        return pages;
    }

    /**
     * 遞迴預熱目錄下所有文件。
     * @return 已觸碰的總頁數
     */
    public static long touchDirectory(File dir) {
        if (!dir.isDirectory()) return 0;
        long totalPages = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isDirectory()) {
                totalPages += touchDirectory(f);
            } else {
                totalPages += touchFile(f);
            }
        }
        return totalPages;
    }

}
