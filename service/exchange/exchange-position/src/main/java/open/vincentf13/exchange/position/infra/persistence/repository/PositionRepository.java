package open.vincentf13.exchange.position.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.common.sdk.enums.PositionSide;
import open.vincentf13.exchange.common.sdk.enums.PositionStatus;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.infra.persistence.mapper.PositionMapper;
import open.vincentf13.exchange.position.infra.persistence.po.PositionPO;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import open.vincentf13.sdk.infra.mysql.OpenMybatisBatchExecutor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Validated
public class PositionRepository {
    
    private final PositionMapper mapper;
    private final DefaultIdGenerator idGenerator;
    private final SqlSessionFactory sqlSessionFactory;
    
    public Position createDefault(@NotNull Long userId,
                                  @NotNull Long instrumentId) {
        Position domain = Position.createDefault(userId, instrumentId, PositionSide.LONG, null);
        domain.setPositionId(idGenerator.newLong());
        PositionPO po = OpenObjectMapper.convert(domain, PositionPO.class);
        try {
            mapper.insert(po);
            return normalize(OpenObjectMapper.convert(po, Position.class));
        } catch (DuplicateKeyException duplicateKeyException) {
            LambdaQueryWrapper<PositionPO> wrapper = Wrappers.lambdaQuery(PositionPO.class)
                                                             .eq(PositionPO::getUserId, userId)
                                                             .eq(PositionPO::getInstrumentId, instrumentId)
                                                             .eq(PositionPO::getStatus, PositionStatus.ACTIVE);
            return findOne(wrapper)
                           .orElseThrow(() -> duplicateKeyException);
        }
    }
    
    public void insertSelective(@NotNull @Valid Position position) {
        if (position.getPositionId() == null) {
            position.setPositionId(idGenerator.newLong());
        }
        PositionPO po = OpenObjectMapper.convert(position, PositionPO.class);
        mapper.insert(po);
    }
    
    public List<Position> findBy(@NotNull LambdaQueryWrapper<PositionPO> wrapper) {
        return mapper.selectList(wrapper).stream()
                     .map(item -> normalize(OpenObjectMapper.convert(item, Position.class)))
                     .toList();
    }
    
    public Optional<Position> findOne(@NotNull LambdaQueryWrapper<PositionPO> wrapper) {
        PositionPO po = mapper.selectOne(wrapper);
        return Optional.ofNullable(normalize(OpenObjectMapper.convert(po, Position.class)));
    }
    
    public boolean updateSelectiveBy(@NotNull @Valid Position update,
                                     LambdaUpdateWrapper<PositionPO> updateWrapper) {
        PositionPO record = OpenObjectMapper.convert(update, PositionPO.class);
        return mapper.update(record, updateWrapper) > 0;
    }

    public void updateSelectiveBatch(@NotNull List<@Valid PositionUpdateTask> tasks) {
        if (tasks.isEmpty()) {
            return;
        }
        OpenMybatisBatchExecutor batchExecutor = new OpenMybatisBatchExecutor(sqlSessionFactory);
        batchExecutor.execute(tasks, (session, task) -> {
            PositionMapper batchMapper = session.getMapper(PositionMapper.class);
            PositionPO record = OpenObjectMapper.convert(task.position(), PositionPO.class);
            LambdaUpdateWrapper<PositionPO> updateWrapper = Wrappers.lambdaUpdate(PositionPO.class)
                                                                    .eq(PositionPO::getPositionId, record.getPositionId())
                                                                    .eq(PositionPO::getVersion, task.expectedVersion());
            batchMapper.update(record, updateWrapper);
        });
    }

    public record PositionUpdateTask(Position position, int expectedVersion) {
    }

    private Position normalize(Position position) {
        if (position == null) {
            return null;
        }
        position.setLeverage(position.getLeverage() == null ? 1 : position.getLeverage());
        position.setMargin(safe(position.getMargin()));
        position.setEntryPrice(safe(position.getEntryPrice()));
        position.setQuantity(safe(position.getQuantity()));
        position.setClosingReservedQuantity(safe(position.getClosingReservedQuantity()));
        position.setMarkPrice(safe(position.getMarkPrice()));
        position.setMarginRatio(safe(position.getMarginRatio()));
        position.setUnrealizedPnl(safe(position.getUnrealizedPnl()));
        position.setLiquidationPrice(safe(position.getLiquidationPrice()));
        position.setCumRealizedPnl(safe(position.getCumRealizedPnl()));
        position.setCumFee(safe(position.getCumFee()));
        position.setCumFundingFee(safe(position.getCumFundingFee()));
        return position;
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
