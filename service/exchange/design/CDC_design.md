
# CDC

使用 Kafka connect +  Debezium ，訂閱 MySQL binlog
- 交易內同時寫：業務表 + `outbox`。
- CDC 監聽 binlog，按行路由到 Topic。
- 去重靠 `event_id` 唯一鍵；順序靠 `seq`。

## 事件外送 Outbox 表

- 用途：將要對外發布的事件與負載持久化，與撮合結果同一事務提交，避免掉事件。     
- Topic 由 **aggregate_type（或 event_type）→ 規則路由**決定

詳細 SQL 請參考 [init.sql](init.sql)
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
	
詳細 SQL 請參考 [init.sql](init.sql)
    
## 死信表
- 需要：你要在資料庫內長期追溯不可處理事件，支持 SQL 查詢、審計、人工修復流程，或下游不走 Kafka。
- 不需要：採用 **Kafka DLQ Topic** 做死信，觀測用 Kafka + APM，即不再落 DB。 (首選)
    
詳細 SQL 請參考 [init.sql](init.sql)
