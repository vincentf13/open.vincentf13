package open.vincentf13.exchange.risk.infra.persistence.repository;

import open.vincentf13.exchange.risk.domain.model.LiquidationTask;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LiquidationTaskRepository {

    LiquidationTask enqueue(LiquidationTask task);

    Optional<LiquidationTask> findById(Long taskId);

    List<LiquidationTask> findPending(int limit);

    boolean markProcessing(Long taskId, Instant startedAt);

    boolean markCompleted(Long taskId, Instant processedAt, BigDecimal liquidationPrice, BigDecimal liquidationPnl);

    boolean markFailed(Long taskId, String errorMessage, int retryCount, Instant processedAt);
}
