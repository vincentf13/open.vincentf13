package open.vincentf13.exchange.risk.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.risk.domain.model.LiquidationTask;
import open.vincentf13.exchange.risk.infra.persistence.mapper.LiquidationTaskMapper;
import open.vincentf13.exchange.risk.infra.persistence.po.LiquidationTaskPO;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class LiquidationTaskRepositoryImpl implements LiquidationTaskRepository {

    private final LiquidationTaskMapper mapper;
    private final DefaultIdGenerator idGenerator;

    @Override
    public LiquidationTask enqueue(LiquidationTask task) {
        if (task == null) {
            return null;
        }
        LiquidationTaskPO po = OpenMapstruct.map(task, LiquidationTaskPO.class);
        Instant now = Instant.now();
        po.setTaskId(idGenerator.newLong());
        po.setQueuedAt(po.getQueuedAt() == null ? now : po.getQueuedAt());
        po.setUpdatedAt(now);
        po.setCreatedAt(po.getCreatedAt() == null ? now : po.getCreatedAt());
        mapper.insert(po);
        return OpenMapstruct.map(po, LiquidationTask.class);
    }

    @Override
    public Optional<LiquidationTask> findById(Long taskId) {
        LiquidationTaskPO po = mapper.findById(taskId);
        return Optional.ofNullable(OpenMapstruct.map(po, LiquidationTask.class));
    }

    @Override
    public List<LiquidationTask> findPending(int limit) {
        return OpenMapstruct.mapList(mapper.findPending(limit), LiquidationTask.class);
    }

    @Override
    public boolean markProcessing(Long taskId, Instant startedAt) {
        return mapper.markProcessing(taskId, startedAt) > 0;
    }

    @Override
    public boolean markCompleted(Long taskId, Instant processedAt, BigDecimal liquidationPrice, BigDecimal liquidationPnl) {
        return mapper.markCompleted(taskId, processedAt, liquidationPrice, liquidationPnl) > 0;
    }

    @Override
    public boolean markFailed(Long taskId, String errorMessage, int retryCount, Instant processedAt) {
        return mapper.markFailed(taskId, errorMessage, retryCount, processedAt) > 0;
    }
}
