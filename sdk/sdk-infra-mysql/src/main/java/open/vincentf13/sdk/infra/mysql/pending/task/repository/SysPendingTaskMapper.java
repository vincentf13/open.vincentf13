package open.vincentf13.sdk.infra.mysql.pending.task.repository;

import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

public interface SysPendingTaskMapper {
    
    void insert(SysPendingTaskPO record);
    
    SysPendingTaskPO findById(@Param("id") Long id);
    
    List<SysPendingTaskPO> fetchRunnable(@Param("limit") int limit);
    
    int markProcessing(@Param("id") Long id,
                       @Param("expectedVersion") Integer expectedVersion);
    
    int markSuccess(@Param("id") Long id,
                    @Param("expectedVersion") Integer expectedVersion,
                    @Param("resultMsg") String resultMsg);
    
    int markFailRetry(@Param("id") Long id,
                      @Param("expectedVersion") Integer expectedVersion,
                      @Param("resultMsg") String resultMsg,
                      @Param("nextRunTime") Instant nextRunTime);
    
    int markFailTerminal(@Param("id") Long id,
                         @Param("expectedVersion") Integer expectedVersion,
                         @Param("resultMsg") String resultMsg);
    
    int rescueStuck(@Param("timeoutThreshold") Instant timeoutThreshold,
                    @Param("resultMsg") String resultMsg);
}
