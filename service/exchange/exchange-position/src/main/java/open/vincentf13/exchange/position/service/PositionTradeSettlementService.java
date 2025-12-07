package open.vincentf13.exchange.position.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.sdk.mq.event.TradeMarginSettledEvent;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.domain.service.PositionDomainService;
import open.vincentf13.exchange.position.infra.messaging.publisher.PositionEventPublisher;
import open.vincentf13.exchange.position.sdk.mq.event.PositionUpdatedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;

@Service
@Validated
@RequiredArgsConstructor
public class PositionTradeSettlementService {

    private final PositionDomainService positionDomainService;
    private final PositionEventPublisher positionEventPublisher;

    @Transactional
    public void handleTradeMarginSettled(@NotNull @Valid TradeMarginSettledEvent event) {
        Collection<Position> positions = positionDomainService.processMarginSettled(
                event.userId(),
                event.instrumentId(),
                event.side(),
                event.price(),
                event.quantity(),
                event.marginUsed(),
                event.feeCharged(),
                event.feeRefund(),
                event.tradeId(),
                event.executedAt());

        for (Position position : positions) {
            positionEventPublisher.publishUpdated(new PositionUpdatedEvent(
                    position.getUserId(),
                    position.getInstrumentId(),
                    position.getSide(),
                    position.getQuantity(),
                    position.getEntryPrice(),
                    position.getMarkPrice(),
                    position.getUnrealizedPnl(),
                    position.getLiquidationPrice(),
                    event.settledAt()
            ));

        }
    }
}
