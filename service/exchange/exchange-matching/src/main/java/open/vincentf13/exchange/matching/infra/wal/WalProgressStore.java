package open.vincentf13.exchange.matching.infra.wal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import open.vincentf13.exchange.matching.infra.MatchingEvent;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class WalProgressStore {

  public long loadLastProcessedSeq(Long instrumentId) {
    Path path = getPath(instrumentId);
    if (!Files.exists(path)) {
      return 0L;
    }
    try {
      String json = Files.readString(path);
      WalProgress progress = OpenObjectMapper.fromJson(json, WalProgress.class);
      return progress != null ? progress.lastProcessedSeq() : 0L;
    } catch (IOException ex) {
      OpenLog.error(MatchingEvent.WAL_PROGRESS_LOAD_FAILED, ex, "instrumentId", instrumentId);
      return 0L;
    }
  }

  public void saveLastProcessedSeq(Long instrumentId, long seq) {
    Path path = getPath(instrumentId);
    try {
      if (path.getParent() != null) {
        Files.createDirectories(path.getParent());
      }
      Files.writeString(path, OpenObjectMapper.toJson(new WalProgress(seq)));
    } catch (IOException ex) {
      OpenLog.error(MatchingEvent.WAL_PROGRESS_SAVE_FAILED, ex, "instrumentId", instrumentId);
    }
  }

  private Path getPath(Long instrumentId) {
    return Path.of("data/matching/loader-progress-" + instrumentId + ".json");
  }

  private record WalProgress(long lastProcessedSeq) {}
}
