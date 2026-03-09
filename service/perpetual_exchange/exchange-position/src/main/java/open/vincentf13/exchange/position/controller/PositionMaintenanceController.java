package open.vincentf13.exchange.position.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.position.infra.bootstrap.StartupCacheLoader;
import open.vincentf13.exchange.position.sdk.rest.api.PositionMaintenanceApi;
import open.vincentf13.sdk.infra.kafka.consumer.reset.KafkaConsumerResetService;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/position/maintenance")
public class PositionMaintenanceController implements PositionMaintenanceApi {

  private final StartupCacheLoader startupCacheLoader;
  private final KafkaConsumerResetService kafkaConsumerResetService;

  @Override
  public OpenApiResponse<Void> reloadCaches() {
    startupCacheLoader.loadCaches();
    kafkaConsumerResetService.resetConsumers();
    return OpenApiResponse.success(null);
  }
}
