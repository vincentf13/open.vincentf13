package open.vincentf13.exchange.account.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.service.AccountCommandService;
import open.vincentf13.exchange.position.sdk.mq.event.PositionCloseToOpenCompensationEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionTopics;
import open.vincentf13.sdk.core.validator.OpenValidator;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PositionCloseToOpenCompensationEventListener {

  private final AccountCommandService accountCommandService;

  @KafkaListener(
      topics = PositionTopics.Names.POSITION_CLOSE_TO_OPEN_COMPENSATION,
      groupId = "${open.vincentf13.exchange.account.consumer-group:exchange-account}")
  public void onCloseToOpenCompensation(
      @Payload PositionCloseToOpenCompensationEvent event, Acknowledgment acknowledgment) {
    OpenValidator.validateOrThrow(event);
    accountCommandService.handleCloseToOpenCompensation(event);
    acknowledgment.acknowledge();
  }
}
