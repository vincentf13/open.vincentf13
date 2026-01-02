package open.vincentf13.exchange.account.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.service.AccountTransactionDomainService;
import open.vincentf13.exchange.account.infra.AccountErrorCode;
import open.vincentf13.exchange.account.infra.AccountEvent;
import open.vincentf13.exchange.account.infra.cache.InstrumentCache;
import open.vincentf13.exchange.account.infra.messaging.publisher.FundsFreezeEventPublisher;
import open.vincentf13.exchange.account.sdk.mq.event.FundsFreezeFailedEvent;
import open.vincentf13.exchange.account.sdk.mq.event.FundsFrozenEvent;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.order.mq.event.FundsFreezeRequestedEvent;
import open.vincentf13.exchange.order.mq.topic.OrderTopics;
import open.vincentf13.sdk.core.OpenValidator;
import open.vincentf13.sdk.core.exception.OpenException;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FundsFreezeRequestedEventListener {

    private final AccountTransactionDomainService accountTransactionDomainService;
    private final InstrumentCache instrumentCache;
    private final FundsFreezeEventPublisher fundsFreezeEventPublisher;

    @KafkaListener(topics = OrderTopics.Names.FUNDS_FREEZE_REQUESTED,
                   groupId = "${open.vincentf13.exchange.account.consumer-group:exchange-account}")
    public void onFundsFreezeRequested(@Payload FundsFreezeRequestedEvent event,
                                       Acknowledgment acknowledgment) {
        AssetSymbol asset = AssetSymbol.UNKNOWN;
        BigDecimal amount = BigDecimal.ZERO;
        try {
            OpenValidator.validateOrThrow(event);
            var instrument = instrumentCache.get(event.instrumentId())
                                            .orElseThrow(() -> OpenException.of(AccountErrorCode.INVALID_EVENT, Map.of("orderId", event.orderId(), "reason", "Instrument not found")));
            asset = instrument.quoteAsset();
            amount = event.requiredMargin().add(event.fee());
            AccountTransactionDomainService.FundsFreezeResult result = accountTransactionDomainService.freezeForOrder(event, instrument);
            fundsFreezeEventPublisher.publishFrozen(
                    new FundsFrozenEvent(event.orderId(), event.userId(), event.instrumentId(), result.asset(), result.amount(), Instant.now())
            );
            acknowledgment.acknowledge();
        } catch (Exception e) {
            fundsFreezeEventPublisher.publishFreezeFailed(
                    new FundsFreezeFailedEvent(event.orderId(), event.userId(), event.instrumentId(),
                            asset, amount, e.getMessage(), Instant.now()));
            OpenLog.warn(AccountEvent.FUNDS_FREEZE_REQUEST_PAYLOAD_INVALID, e, "event", event);
            throw e;
        }
    }
}
