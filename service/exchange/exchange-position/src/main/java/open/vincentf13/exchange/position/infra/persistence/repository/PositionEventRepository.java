package open.vincentf13.exchange.position.infra.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.position.domain.model.PositionEvent;
import open.vincentf13.exchange.position.infra.PositionErrorCode;
import open.vincentf13.exchange.position.infra.persistence.mapper.PositionEventMapper;
import open.vincentf13.exchange.position.infra.persistence.po.PositionEventPO;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionReferenceType;
import open.vincentf13.sdk.core.exception.OpenException;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

@Repository
@RequiredArgsConstructor
@Validated
public class PositionEventRepository {

    private final PositionEventMapper mapper;
    private final DefaultIdGenerator idGenerator;

    public void insert(@NotNull @Valid PositionEvent event) {
        if (event.getEventId() == null) {
            event.setEventId(idGenerator.newLong());
        }
        PositionEventPO po = OpenObjectMapper.convert(event, PositionEventPO.class);
        mapper.insert(po);
    }

    public boolean existsByReference(@NotNull PositionReferenceType referenceType,
                                     @NotNull String referenceId) {
        try {
            return mapper.selectCount(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(PositionEventPO.class)
                            .eq(PositionEventPO::getReferenceType, referenceType)
                            .eq(PositionEventPO::getReferenceId, referenceId)) > 0;
        } catch (DataAccessException e) {
            throw OpenException.of(PositionErrorCode.POSITION_CONCURRENT_UPDATE, Map.of("referenceId", referenceId), e);
        }
    }
    
    public boolean existsByReferenceAndUser(@NotNull PositionReferenceType referenceType,
                                            @NotNull String referenceId,
                                            @NotNull Long userId) {
        return mapper.selectCount(Wrappers.lambdaQuery(PositionEventPO.class)
                                          .eq(PositionEventPO::getReferenceType, referenceType)
                                          .eq(PositionEventPO::getReferenceId, referenceId)
                                          .eq(PositionEventPO::getUserId, userId)) > 0;
    }
}
