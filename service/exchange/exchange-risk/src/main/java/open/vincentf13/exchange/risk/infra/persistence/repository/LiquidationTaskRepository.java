package open.vincentf13.exchange.risk.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.risk.domain.model.LiquidationTask;
import open.vincentf13.exchange.risk.infra.persistence.mapper.LiquidationTaskMapper;
import open.vincentf13.exchange.risk.infra.persistence.po.LiquidationTaskPO;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Validated
public class LiquidationTaskRepository {

    private final LiquidationTaskMapper mapper;
    private final DefaultIdGenerator idGenerator;

    public LiquidationTask enqueue(@NotNull @Valid LiquidationTask task) {
        LiquidationTaskPO po = OpenMapstruct.map(task, LiquidationTaskPO.class);
        Instant now = Instant.now();
        po.setTaskId(idGenerator.newLong());
        po.setQueuedAt(po.getQueuedAt() == null ? now : po.getQueuedAt());
        mapper.insertSelective(po);
        return OpenMapstruct.map(po, LiquidationTask.class);
    }

    public Optional<LiquidationTask> findById(@NotNull Long taskId) {
        LiquidationTaskPO condition = new LiquidationTaskPO();
        condition.setTaskId(taskId);
        LiquidationTaskPO po = mapper.findBy(condition);
        return Optional.ofNullable(OpenMapstruct.map(po, LiquidationTask.class));
    }

    public List<LiquidationTask> findPending(int limit) {
        return OpenMapstruct.mapList(mapper.findPending(limit), LiquidationTask.class);
    }

    public boolean markProcessing(@NotNull Long taskId, @NotNull Instant startedAt) {
        return mapper.markProcessing(taskId, startedAt) > 0;
    }

    public boolean markCompleted(@NotNull Long taskId, @NotNull Instant processedAt, @NotNull BigDecimal liquidationPrice, @NotNull BigDecimal liquidationPnl) {
        return mapper.markCompleted(taskId, processedAt, liquidationPrice, liquidationPnl) > 0;
    }

    public boolean markFailed(@NotNull Long taskId, String errorMessage, int retryCount, @NotNull Instant processedAt) {
        return mapper.markFailed(taskId, errorMessage, retryCount, processedAt) > 0;
    }
}
