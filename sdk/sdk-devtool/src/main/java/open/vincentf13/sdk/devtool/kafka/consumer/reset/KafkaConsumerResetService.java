package open.vincentf13.sdk.devtool.kafka.consumer.reset;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerResetService {

  private final KafkaListenerEndpointRegistry registry;

  public void resetConsumers() {
    log.info("Resetting all Kafka consumers...");

    registry
        .getListenerContainers()
        .forEach(
            container -> {
              try {
                if (container.isRunning()) {
                  log.info("Stopping container: {}", container.getListenerId());
                  container.stop();
                }

                log.info("Starting container: {}", container.getListenerId());
                container.start();

              } catch (Exception e) {
                log.error("Failed to reset container: {}", container.getListenerId(), e);
              }
            });

    log.info("Kafka consumers reset completed.");
  }
}
