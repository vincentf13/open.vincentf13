- Topic常數、事件 Payload 與集中在 `exchange-{service}-sdk-mq` 契約模組，方便訂閱者引入。
- **發布訊息**:  透過 `sdk-infra-mysql` 的 Outbox (`MqOutboxRepository#append`) 將事件寫入 `mq_outbox`，表由 Debezium CDC
  訂閱 binlog 推送至 Kafka ，


- **生產者設定**:
    - 統一依 `sdk-infra-kafka-defaults.yaml` 預設配置：
    - Producer 端採 `StringSerializer`/`JsonSerializer`，並啟用 `spring.json.add.type.headers=true`，讓消費者能依
      `__TypeId__` 自動反序列化對應事件模型。
    - 若透過 Outbox/CDC 發布，Producer 實際只需寫 DB；自研服務要直接推 Kafka 時，仍應沿用這組 `KafkaTemplate` 與配置，以確保行為一致。
- **消費者設定**:
    - 統一依 `sdk-infra-kafka-defaults.yaml` 預設配置。
    - `@KafkaListener` 的 `@Payload` 可以自動轉成對應事件類別 。( 因使用 `StringDeserializer`/`JsonDeserializer`且Producer
      端開啟 `spring.json.add.type.headers=true`)
    - 消費者未補獲的例外，失敗由 `DefaultErrorHandler` 依 `FixedBackOff(1000ms,2)` 自動重試，耗盡後交由 `.DLT`。
    - `spring.kafka.listener.type=batch` 並配合 `ack-mode=manual`，listener 需注入 `Acknowledgment` 並在成功處理後呼叫
      `acknowledge()` 才會提交 offset
