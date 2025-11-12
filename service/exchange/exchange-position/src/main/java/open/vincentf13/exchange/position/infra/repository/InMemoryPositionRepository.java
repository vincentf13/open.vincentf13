package open.vincentf13.exchange.position.infra.repository;

import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderSide;
import open.vincentf13.exchange.position.domain.model.Position;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryPositionRepository implements PositionRepository {

    private final Map<PositionKey, Position> storage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1000L);

    @Override
    public Optional<Position> findActive(Long userId, Long instrumentId) {
        if (userId == null || instrumentId == null) {
            return Optional.empty();
        }
        Position position = storage.get(PositionKey.of(userId, instrumentId));
        return Optional.ofNullable(position);
    }

    @Override
    public List<Position> findByUser(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<Position> result = new ArrayList<>();
        storage.forEach((key, value) -> {
            if (userId.equals(key.userId())) {
                result.add(value);
            }
        });
        return result;
    }

    @Override
    public Position save(Position position) {
        PositionKey key = PositionKey.of(position.getUserId(), position.getInstrumentId());
        if (position.getPositionId() == null) {
            position.setPositionId(idGenerator.incrementAndGet());
        }
        position.setUpdatedAt(Instant.now());
        storage.put(key, position);
        return position;
    }

    @Override
    public boolean reserveForClose(Long userId, Long instrumentId, BigDecimal quantity) {
        if (userId == null || instrumentId == null || quantity == null) {
            return false;
        }
        PositionKey key = PositionKey.of(userId, instrumentId);
        final boolean[] success = {false};
        storage.computeIfPresent(key, (k, position) -> {
            BigDecimal available = position.availableToClose();
            if (available.compareTo(quantity) < 0) {
                return position;
            }
            BigDecimal reserved = position.getClosingReservedQuantity() == null
                    ? BigDecimal.ZERO
                    : position.getClosingReservedQuantity();
            position.setClosingReservedQuantity(reserved.add(quantity));
            position.setUpdatedAt(Instant.now());
            success[0] = true;
            return position;
        });
        return success[0];
    }

    @Override
    public void releaseReserve(Long userId, Long instrumentId, BigDecimal quantity) {
        PositionKey key = PositionKey.of(userId, instrumentId);
        storage.computeIfPresent(key, (k, position) -> {
            BigDecimal reserved = position.getClosingReservedQuantity() == null
                    ? BigDecimal.ZERO
                    : position.getClosingReservedQuantity();
            BigDecimal remaining = reserved.subtract(quantity);
            if (remaining.signum() < 0) {
                remaining = BigDecimal.ZERO;
            }
            position.setClosingReservedQuantity(remaining);
            position.setUpdatedAt(Instant.now());
            return position;
        });
    }

    @Override
    public Position upsertDemoPosition(Long userId, Long instrumentId, OrderSide side, BigDecimal quantity) {
        Position position = Position.builder()
                .userId(userId)
                .instrumentId(instrumentId)
                .side(side)
                .quantity(quantity)
                .closingReservedQuantity(BigDecimal.ZERO)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return save(position);
    }

    private record PositionKey(Long userId, Long instrumentId) {
        private static PositionKey of(Long userId, Long instrumentId) {
            return new PositionKey(userId, instrumentId);
        }
    }
}
