package open.vincentf13.exchange.position.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionSide;
import open.vincentf13.exchange.position.infra.persistence.mapper.PositionMapper;
import open.vincentf13.exchange.position.infra.persistence.po.PositionPO;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

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
        PositionPO po = mapper.findBy(condition);
        return Optional.ofNullable(OpenMapstruct.map(po, Position.class));
    }

    public Optional<Position> findById(@NotNull Long positionId) {
        PositionPO condition = PositionPO.builder()
                .positionId(positionId)
                .build();
        PositionPO po = mapper.findBy(condition);
        return Optional.ofNullable(OpenMapstruct.map(po, Position.class));
    }

    public Position createDefault(@NotNull Long userId, @NotNull Long instrumentId) {
        Position domain = Position.createDefault(userId, instrumentId, PositionSide.LONG);
        domain.setPositionId(idGenerator.newLong());
        PositionPO po = OpenMapstruct.map(domain, PositionPO.class);
        try {
            mapper.insertDefault(po);
            return OpenMapstruct.map(po, Position.class);
        } catch (DuplicateKeyException duplicateKeyException) {
            PositionPO existing = PositionPO.builder()
                    .userId(userId)
                    .instrumentId(instrumentId)
                    .status("ACTIVE")
                    .build();
            PositionPO poExisting = mapper.findBy(existing);
            return OpenMapstruct.map(poExisting, Position.class);
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
