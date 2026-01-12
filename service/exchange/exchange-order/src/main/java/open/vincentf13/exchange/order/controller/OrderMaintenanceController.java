package open.vincentf13.exchange.order.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.order.sdk.rest.api.OrderMaintenanceApi;
import open.vincentf13.sdk.devtool.kafka.consumer.reset.KafkaConsumerResetService;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order/maintenance")
@RequiredArgsConstructor
public class OrderMaintenanceController implements OrderMaintenanceApi {

    private final KafkaConsumerResetService kafkaConsumerResetService;

    @Override
    public OpenApiResponse<Void> reset() {
        kafkaConsumerResetService.resetConsumers();
        return OpenApiResponse.success(null);
    }
}
