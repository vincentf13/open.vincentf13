package open.vincentf13.exchange.position.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.infra.persistence.mapper.PositionMapper;
import open.vincentf13.exchange.position.infra.persistence.po.PositionPO;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionSide;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Validated
public class PositionRepository {

    private final PositionMapper mapper;
    private final DefaultIdGenerator idGenerator;

    public Position createDefault(@NotNull Long userId, @NotNull Long instrumentId) {
        Position domain = Position.createDefault(userId, instrumentId, PositionSide.LONG);
        domain.setPositionId(idGenerator.newLong());
        PositionPO po = OpenObjectMapper.convert(domain, PositionPO.class);
        try {
            mapper.insertSelective(po);
            return OpenObjectMapper.convert(po, Position.class);
        } catch (DuplicateKeyException duplicateKeyException) {
            Position existing = Position.builder()
                    .userId(userId)
                    .instrumentId(instrumentId)
                    .status("ACTIVE")
                    .build();
            return findOne(existing)
                    .orElseThrow(() -> duplicateKeyException);
        }
    }

    public void insertSelective(@NotNull @Valid Position position) {
        if (position.getPositionId() == null) {
            position.setPositionId(idGenerator.newLong());
        }
        PositionPO po = OpenObjectMapper.convert(position, PositionPO.class);
        mapper.insertSelective(po);
    }

    public List<Position> findBy(@NotNull Position condition) {
        PositionPO probe = OpenObjectMapper.convert(condition, PositionPO.class);
        return mapper.findBy(probe).stream()
                .map(item -> OpenObjectMapper.convert(item, Position.class))
                .toList();
    }

    public Optional<Position> findOne(@NotNull Position condition) {
        PositionPO probe = OpenObjectMapper.convert(condition, PositionPO.class);
        var results = mapper.findBy(probe);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        if (results.size() > 1) {
            throw new IllegalStateException("Expected single position but found " + results.size());
        }
        return Optional.of(OpenObjectMapper.convert(results.get(0), Position.class));
    }

    public boolean updateSelectiveBy(@NotNull @Valid Position update,
                                     @NotNull Long positionId,
                                     Long userId,
                                     Long instrumentId,
                                     PositionSide side,
                                     Integer expectedVersion,
                                     String status) {
        PositionPO record = OpenObjectMapper.convert(update, PositionPO.class);
        return mapper.updateSelectiveBy(record, positionId, userId, instrumentId, side, expectedVersion, status) > 0;
    }
}
