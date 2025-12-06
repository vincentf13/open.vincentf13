package open.vincentf13.exchange.position.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.common.sdk.enums.PositionSide;
import open.vincentf13.exchange.common.sdk.enums.PositionStatus;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.infra.PositionErrorCode;
import open.vincentf13.exchange.position.infra.messaging.publisher.PositionEventPublisher;
import open.vincentf13.exchange.position.infra.persistence.po.PositionPO;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionRepository;
import open.vincentf13.exchange.position.sdk.mq.event.PositionReserveRejectedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionReserveRequestedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionReservedEvent;
import open.vincentf13.sdk.core.exception.OpenException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Service
@Validated
@RequiredArgsConstructor
public class PositionReserveCommandService {

    private final PositionRepository positionRepository;
    private final PositionEventPublisher positionEventPublisher;

    public void handlePositionReserveRequested(@NotNull @Valid PositionReserveRequestedEvent event) {
        Position position = positionRepository.findOne(
                        Wrappers.lambdaQuery(PositionPO.class)
                                .eq(PositionPO::getUserId, event.userId())
                                .eq(PositionPO::getInstrumentId, event.instrumentId())
                                .eq(PositionPO::getStatus, PositionStatus.ACTIVE))
                .orElse(null);

        if (position == null) {
            publishRejected(event, "POSITION_NOT_FOUND", null);
            return;
        }

        BigDecimal availableToClose = position.availableToClose();
        if (availableToClose.compareTo(event.quantity()) < 0) {
            publishRejected(event, "INSUFFICIENT_AVAILABLE", position.getSide());
            return;
        }

        int expectedVersion = position.safeVersion();
        Position updateRecord = Position.builder()
                                        .positionId(position.getPositionId())
                                        .closingReservedQuantity(position.getClosingReservedQuantity().add(event.quantity()))
                                        .version(expectedVersion + 1)
                                        .build();

        boolean updated = positionRepository.updateSelectiveBy(
                updateRecord,
                new LambdaUpdateWrapper<PositionPO>()
                        .eq(PositionPO::getPositionId, position.getPositionId())
                        .eq(PositionPO::getUserId, position.getUserId())
                        .eq(PositionPO::getInstrumentId, position.getInstrumentId())
                        .eq(PositionPO::getStatus, PositionStatus.ACTIVE)
                        .eq(PositionPO::getVersion, expectedVersion));

        if (!updated) {
            throw OpenException.of(PositionErrorCode.POSITION_CONCURRENT_UPDATE,
                                   Map.of("positionId", position.getPositionId()));
        }

        positionEventPublisher.publishReserved(new PositionReservedEvent(
                event.orderId(),
                event.userId(),
                event.instrumentId(),
                position.getSide(),
                event.quantity(),
                position.getEntryPrice(),
                Instant.now()
        ));
    }

    private void publishRejected(PositionReserveRequestedEvent event, String reason, PositionSide side) {
        positionEventPublisher.publishReserveRejected(new PositionReserveRejectedEvent(
                event.orderId(),
                event.userId(),
                event.instrumentId(),
                side,
                event.quantity(),
                reason,
                Instant.now()
        ));
    }
}
