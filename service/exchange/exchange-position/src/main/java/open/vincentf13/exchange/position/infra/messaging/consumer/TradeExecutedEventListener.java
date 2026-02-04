package open.vincentf13.exchange.position.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.matching.sdk.mq.topic.MatchingTopics;
import open.vincentf13.exchange.position.infra.PositionErrorCode;
import open.vincentf13.exchange.position.infra.PositionEvent;
import open.vincentf13.exchange.position.service.PositionCommandService;
import open.vincentf13.sdk.core.exception.OpenException;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeExecutedEventListener {

  private final PositionCommandService positionCommandService;

  @KafkaListener(
      topics = MatchingTopics.Names.TRADE_EXECUTED,
      groupId =
          "${exchange.position.trade-executed.consumer-group:exchange-position-trade-executed}")
  public void onTradeExecuted(@Payload TradeExecutedEvent event, Acknowledgment acknowledgment) {
    try {
      positionCommandService.handleTradeExecuted(event);
      acknowledgment.acknowledge();
    } catch (OpenException e) {
      if (e.getCode() == PositionErrorCode.DUPLICATE_REQUEST) {
        OpenLog.warn(log, PositionEvent.POSITION_TRADE_DUPLICATE, e, "event", event);
        acknowledgment.acknowledge();
        return;
      }
      throw e;
    }
  }
}
