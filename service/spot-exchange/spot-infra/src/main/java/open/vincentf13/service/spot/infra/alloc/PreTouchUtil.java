package open.vincentf13.service.spot.infra.alloc;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

/**
 * 記憶體頁面預熱與鎖定工具 (Pre-touch &amp; mlock Utility)
 *
 * <ul>
 *   <li>Pre-touch：讀取 mmap 文件每一頁（4KB），強迫 OS 分配實體 RAM</li>
 *   <li>mlock：透過 {@link MappedByteBuffer#load()} 通知 OS 將頁面載入實體 RAM
 *       並盡量保持駐留 (madvise MADV_WILLNEED)。配合 {@code -XX:+UseLargePages}
 *       與 OS swap 禁用可達成接近 mlock 的效果。</li>
 * </ul>
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

    /**
     * 透過 {@link MappedByteBuffer#load()} 將文件頁面載入實體 RAM 並建議 OS 保持駐留。
     * 底層執行 madvise(MADV_WILLNEED)，比單純 pre-touch 更積極地提示 OS 保留頁面。
     *
     * @return 成功載入的頁數
     */
    public static long mlockFile(File file) {
        if (!file.isFile() || file.length() == 0) return 0;
        long size = file.length();
        long pages = 0;
        try (FileChannel ch = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            long offset = 0;
            while (offset < size) {
                long chunkSize = Math.min(size - offset, MAX_MAP_CHUNK);
                MappedByteBuffer buf = ch.map(FileChannel.MapMode.READ_ONLY, offset, chunkSize);
                buf.load(); // madvise(MADV_WILLNEED) — 強烈提示 OS 將頁面保持在 RAM
                pages += chunkSize / PAGE_SIZE;
                offset += chunkSize;
            }
        } catch (IOException e) {
            log.debug("[MLOCK] 載入失敗: {} ({})", file.getName(), e.getMessage());
        }
        return pages;
    }

    /**
     * 遞迴 load() 目錄下所有文件。
     * @return 成功載入的總頁數
     */
    public static long mlockDirectory(File dir) {
        if (!dir.isDirectory()) return 0;
        long total = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isDirectory()) total += mlockDirectory(f);
            else total += mlockFile(f);
        }
        return total;
    }
}
