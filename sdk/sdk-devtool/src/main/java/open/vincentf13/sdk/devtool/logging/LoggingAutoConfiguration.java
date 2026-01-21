package open.vincentf13.sdk.devtool.logging;

import open.vincentf13.sdk.devtool.kafka.consumer.controller.KafkaAdminController;
import open.vincentf13.sdk.infra.kafka.consumer.reset.KafkaConsumerResetService;
import open.vincentf13.sdk.infra.kafka.consumer.reset.KafkaDebugOffsetResetConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnWebApplication
@Import({
  LoggingController.class,
  KafkaAdminController.class,
  KafkaConsumerResetService.class,
  KafkaDebugOffsetResetConfiguration.class
})
public class LoggingAutoConfiguration {}
