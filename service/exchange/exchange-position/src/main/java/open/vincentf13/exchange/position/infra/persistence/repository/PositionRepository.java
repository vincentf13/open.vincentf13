package open.vincentf13.exchange.position.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.infra.persistence.mapper.PositionMapper;
import open.vincentf13.exchange.position.infra.persistence.po.PositionPO;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionSide;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Validated
public class PositionRepository {

    private final PositionMapper mapper;
    private final DefaultIdGenerator idGenerator;

    public Optional<Position> findActive(@NotNull Long userId, @NotNull Long instrumentId) {
        PositionPO condition = PositionPO.builder()
                .userId(userId)
                .instrumentId(instrumentId)
                .status("ACTIVE")
                .build();
        return findOne(condition);
    }

    private Optional<Position> findOne(@NotNull @Valid PositionPO condition) {
        var results = mapper.findBy(condition);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        if (results.size() > 1) {
            throw new IllegalStateException("Expected single position but found " + results.size());
        }
        return Optional.of(OpenMapstruct.map(results.get(0), Position.class));
    }

    public Position createDefault(@NotNull Long userId, @NotNull Long instrumentId) {
        Position domain = Position.createDefault(userId, instrumentId, PositionSide.LONG);
        domain.setPositionId(idGenerator.newLong());
        PositionPO po = OpenMapstruct.map(domain, PositionPO.class);
        try {
            mapper.insertSelective(po);
            return OpenMapstruct.map(po, Position.class);
        } catch (DuplicateKeyException duplicateKeyException) {
            PositionPO existing = PositionPO.builder()
                    .userId(userId)
                    .instrumentId(instrumentId)
                    .status("ACTIVE")
                    .build();
            return findOne(existing)
                    .orElseThrow(() -> duplicateKeyException);
        }
    }

    public boolean reserveForClose(@NotNull Long userId,
                                   @NotNull Long instrumentId,
                                   @NotNull @DecimalMin(value = "0.00000001") BigDecimal quantity,
                                   @NotNull PositionSide side,
                                   int expectedVersion) {
        return mapper.reserveForClose(userId, instrumentId, quantity, side, expectedVersion) > 0;
    }

    public boolean updateLeverage(@NotNull Long positionId, @NotNull Integer leverage) {
        return mapper.updateLeverage(positionId, leverage) > 0;
    }

}
