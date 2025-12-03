
# CDC

使用 Kafka connect +  Debezium ，訂閱 MySQL binlog
- 交易內同時寫：業務表 + `outbox`。
- CDC 監聽 binlog，按行路由到 Topic。
- 去重靠 `event_id` 唯一鍵；順序靠 `seq`。

## 事件外送 Outbox 表

- 用途：將要對外發布的事件與負載持久化，與撮合結果同一事務提交，避免掉事件。     
- Topic 由 **aggregate_type（或 event_type）→ 規則路由**決定

```sql
CREATE TABLE mq_outbox (
  event_id      VARCHAR(36)  NOT NULL COMMENT 'UUID 去重鍵',
  aggregate_type VARCHAR(100) NOT NULL COMMENT '原始業務事件的類型，目標Topic 由 aggregate_type → 規則路由決定',
  aggregate_id  BIGINT       NOT NULL COMMENT '原始業務事件的ID',
  event_type    VARCHAR(64)  NOT NULL COMMENT '事件類型',
  payload       JSON         NOT NULL COMMENT '事件內容',
  headers       JSON         NULL COMMENT '可選標頭 trace等',
  seq           BIGINT       NOT NULL COMMENT '業務序號 保序/重放基準',
  created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '生成時間',
  PRIMARY KEY (event_id),
  UNIQUE KEY uk_seq(seq),
  KEY idx_created(created_at),
  KEY idx_agg(aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```
- 訂單事件
    - aggregate_type = 'order'
    - aggregate_id = order_id
- 成交事件
    - aggregate_type = 'trade'
    - aggregate_id = trade_id
- 倉位事件
    - aggregate_type = 'position'
    - aggregate_id = position_id

## 消費者組偏移量表

- 需要：你要用「業務序號 `seq`」做重放、對賬、校驗，而不是只靠 Kafka offset。
- 不需要：完全依賴 Kafka 自帶 offset 與保留期重放，不做以 `seq` 為準的重放/校驗。
	
```sql
-- 以 consumer_group + topic + partition 為粒度
CREATE TABLE mq_consumer_offsets (
  consumer_group VARCHAR(100) NOT NULL COMMENT '消費者群組',
  topic          VARCHAR(100) NOT NULL COMMENT '來源 Topic',
  partition_id   INT          NOT NULL COMMENT '分片編號',
  last_seq       BIGINT       NOT NULL COMMENT '最後處理的序號',
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                               ON UPDATE CURRENT_TIMESTAMP COMMENT '更新時間',
  PRIMARY KEY (consumer_group, topic, partition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
 ```
    
## 死信表
- 需要：你要在資料庫內長期追溯不可處理事件，支持 SQL 查詢、審計、人工修復流程，或下游不走 Kafka。
- 不需要：採用 **Kafka DLQ Topic** 做死信，觀測用 Kafka + APM，即不再落 DB。 (首選)
    
```sql
-- 發送環節或下游無法處理的事件作持久化 避免遺失
CREATE TABLE mq_dead_letters (
  id         BIGINT      NOT NULL PRIMARY KEY COMMENT '死信 ID Snowflake',
  source     VARCHAR(64) NOT NULL COMMENT '來源 例如 outbox matching-engine',
  seq        BIGINT      NULL     COMMENT '業務序號 可為 NULL',
  outbox_id  BIGINT      NULL     COMMENT '對應 Outbox ID',
  payload    JSON        NOT NULL COMMENT '事件資料內容',
  error      TEXT        NOT NULL COMMENT '錯誤詳細內容',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '記錄時間',
  KEY idx_source_time(source, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```
