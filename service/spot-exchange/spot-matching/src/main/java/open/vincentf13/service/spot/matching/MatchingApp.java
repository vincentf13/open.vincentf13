package open.vincentf13.service.spot.matching;

import net.openhft.affinity.AffinityLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "open.vincentf13.service.spot.matching",
    "open.vincentf13.service.spot.infra"
})
public class MatchingApp {
    public static void main(String[] args) {
        // 在 JVM 啟動初期為管理執行緒 (GC, JIT) 預留空間，確保它們不干擾後續啟動的 3 個核心 P-core 執行緒
        try (AffinityLock lock = AffinityLock.acquireCore()) {
            System.out.println(">>> [AFFINITY] JVM 管理執行緒已鎖定核心: " + lock.cpuId());
            Storage.self().metricsHistory().put(Storage.KEY_CPU_ID_JVM_MANAGEMENT, (long) lock.cpuId());
            cleanDataDirectory();
            SpringApplication.run(MatchingApp.class, args);
        }
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
