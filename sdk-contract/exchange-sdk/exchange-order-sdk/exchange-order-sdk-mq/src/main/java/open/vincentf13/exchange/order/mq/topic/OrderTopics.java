package open.vincentf13.exchange.order.mq.topic;

public interface OrderTopics {
    String ORDER_SUBMITTED = "order.submitted";
    String ORDER_CANCEL_REQUESTED = "order.cancel-requested";
    String ORDER_CREATED = "order.created";
}
