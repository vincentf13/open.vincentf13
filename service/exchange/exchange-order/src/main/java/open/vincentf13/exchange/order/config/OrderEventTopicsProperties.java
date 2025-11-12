package open.vincentf13.exchange.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "open.vincentf13.exchange.order.topics")
public class OrderEventTopicsProperties {

    /**
     * Kafka topic for order.submitted events.
     */
    private String orderSubmitted = "order.submitted";

    /**
     * Kafka topic for order.cancel-requested events.
     */
    private String orderCancelRequested = "order.cancel-requested";

    public String getOrderSubmitted() {
        return orderSubmitted;
    }

    public void setOrderSubmitted(String orderSubmitted) {
        if (StringUtils.hasText(orderSubmitted)) {
            this.orderSubmitted = orderSubmitted;
        }
    }

    public String getOrderCancelRequested() {
        return orderCancelRequested;
    }

    public void setOrderCancelRequested(String orderCancelRequested) {
        if (StringUtils.hasText(orderCancelRequested)) {
            this.orderCancelRequested = orderCancelRequested;
        }
    }
}
