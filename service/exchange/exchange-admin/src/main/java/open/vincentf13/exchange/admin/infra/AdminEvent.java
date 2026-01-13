package open.vincentf13.exchange.admin.infra;

import open.vincentf13.sdk.core.log.OpenEvent;

public enum AdminEvent implements OpenEvent {
  KAFKA_CONNECTOR_CREATE_REQUESTED(
      "AdminKafkaConnectorCreateRequested", "Kafka connector creation requested"),
  KAFKA_CONNECTOR_DELETE_FAILED("AdminKafkaConnectorDeleteFailed", "Kafka connector delete failed");

  private final String event;
  private final String message;

  AdminEvent(String event, String message) {
    this.event = event;
    this.message = message;
  }

  @Override
  public String event() {
    return event;
  }

  @Override
  public String message() {
    return message;
  }
}
