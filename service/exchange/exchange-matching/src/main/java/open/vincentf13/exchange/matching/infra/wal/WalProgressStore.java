package open.vincentf13.exchange.matching.infra.wal;

import open.vincentf13.exchange.matching.infra.MatchingEvent;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class WalProgressStore {
    
    private static final Path PROGRESS_PATH = Path.of("data/matching/loader-progress.json");
    
    public long loadLastProcessedSeq() {
        if (!Files.exists(PROGRESS_PATH)) {
            return 0L;
        }
        try {
            String json = Files.readString(PROGRESS_PATH);
            WalProgress progress = OpenObjectMapper.fromJson(json, WalProgress.class);
            return progress != null ? progress.lastProcessedSeq() : 0L;
        } catch (IOException ex) {
            OpenLog.error(MatchingEvent.WAL_PROGRESS_LOAD_FAILED, ex);
            return 0L;
        }
    }
    
    public void saveLastProcessedSeq(long seq) {
        try {
            Files.createDirectories(PROGRESS_PATH.getParent());
            Files.writeString(PROGRESS_PATH, OpenObjectMapper.toJson(new WalProgress(seq)));
        } catch (IOException ex) {
            OpenLog.error(MatchingEvent.WAL_PROGRESS_SAVE_FAILED, ex);
        }
    }
    
    private record WalProgress(long lastProcessedSeq) {
    }
}
