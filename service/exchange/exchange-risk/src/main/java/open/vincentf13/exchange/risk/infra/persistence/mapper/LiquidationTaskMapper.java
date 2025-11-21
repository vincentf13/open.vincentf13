package open.vincentf13.exchange.risk.infra.persistence.mapper;

import open.vincentf13.exchange.risk.infra.persistence.po.LiquidationTaskPO;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

public interface LiquidationTaskMapper {

    int insertSelective(LiquidationTaskPO task);

    LiquidationTaskPO findBy(LiquidationTaskPO condition);

    List<LiquidationTaskPO> findPending(@Param("limit") int limit);

    int markProcessing(@Param("taskId") Long taskId,
                       @Param("startedAt") Instant startedAt);

    int markCompleted(@Param("taskId") Long taskId,
                      @Param("processedAt") Instant processedAt,
                      @Param("liquidationPrice") java.math.BigDecimal liquidationPrice,
                      @Param("liquidationPnl") java.math.BigDecimal liquidationPnl);

    int markFailed(@Param("taskId") Long taskId,
                   @Param("errorMessage") String errorMessage,
                   @Param("retryCount") int retryCount,
                   @Param("processedAt") Instant processedAt);
}
