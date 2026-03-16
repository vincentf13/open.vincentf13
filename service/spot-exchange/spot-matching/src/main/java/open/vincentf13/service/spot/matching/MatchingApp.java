package open.vincentf13.service.spot.matching;

import net.openhft.affinity.AffinityLock;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "open.vincentf13.service.spot.matching",
    "open.vincentf13.service.spot.infra"
})
public class MatchingApp {
    public static void main(String[] args) {
        // 移除 main 執行緒的強制綁核，避免與 PowerShell 的 ProcessorAffinity 衝突導致 Error 87
        // JVM 管理執行緒將由 OS 在進程允許的核心範圍內自動調度
        cleanDataDirectory();
        SpringApplication.run(MatchingApp.class, args);
    }

    private static void cleanDataDirectory() {
        String path = "C:/iProject/open.vincentf13/data/spot-exchange/";
        java.io.File dir = new java.io.File(path);
        if (dir.exists()) {
            System.out.println(">>> [INIT] 正在清空數據目錄 (排除 aeron, logs): " + path);
            java.io.File[] children = dir.listFiles();
            if (children != null) {
                for (java.io.File child : children) {
                    String name = child.getName();
                    if (!name.equals("aeron") && !name.equals("logs")) {
                        deleteRecursive(child);
                    }
                }
            }
            System.out.println(">>> [INIT] 數據目錄清理完成。");
        }
    }

    private static void deleteRecursive(java.io.File file) {
        if (file.isDirectory()) {
            java.io.File[] children = file.listFiles();
            if (children != null) {
                for (java.io.File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
}
