package open.vincentf13.service.spot.matching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "open.vincentf13.service.spot.matching",
    "open.vincentf13.service.spot.infra"
})
public class MatchingApp {
    public static void main(String[] args) {
        cleanDataDirectory();
        SpringApplication.run(MatchingApp.class, args);
    }

    private static void cleanDataDirectory() {
        String path = "C:/iProject/open.vincentf13/data/spot-exchange/";
        java.io.File dir = new java.io.File(path);
        if (dir.exists()) {
            System.out.println(">>> [INIT] 正在清空數據目錄: " + path);
            deleteRecursive(dir);
            System.out.println(">>> [INIT] 數據目錄已清空。");
        }
    }

    private static void deleteRecursive(java.io.File file) {
        java.io.File[] children = file.listFiles();
        if (children != null) {
            for (java.io.File child : children) {
                deleteRecursive(child);
            }
        }
        file.delete();
    }
}
