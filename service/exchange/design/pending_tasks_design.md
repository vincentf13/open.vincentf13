
詳細 SQL 請參考 [SQL.sql](./SQL.sql)

---

### 欄位設計邏輯解析
- **biz_type & biz_key (業務路由)**
    - `biz_type`: 區分這是什麼任務（例如：發送簡訊、同步訂單、計算報表）。Worker 程式可以根據這個欄位決定呼叫哪個 Handler。
    - `biz_key`: 業務上的唯一 ID（例如訂單號）。設置 Unique Key 可以防止同一個訂單被重複插入 Pending 表（保證冪等性）。
- **payload (數據通用性)**
    - 使用 `JSON` 類型是最關鍵的設計。無論是發送 Email 需要的 `{"to": "xx", "subject": "xx"}` 還是訂單處理需要的 `{"orderId": 123, "amount": 100}`，都可以存入此欄位，無需為每種業務修改 Table 結構。
- **status & priority (流程控制)**
    - 狀態機設計建議簡單化：Pending (待處理) -> Processing (鎖定中) -> Success (移至歷史表或標記完成) / Fail (待重試)。
    - 優先級欄位允許緊急任務（如 OTP 簡訊）插隊。
- **next_run_time (排程與退避)**
    - 不要只依賴 `created_at`。`next_run_time` 允許你實現「延遲 5 分鐘後執行」或「失敗後 10 分鐘再重試（指數退避）」的功能。Worker 查詢時應 `WHERE next_run_time <= NOW()`。
- **version (並發安全)**
    - 在分佈式系統中，可能有多個 Worker 同時掃描表格。利用樂觀鎖（Optimistic Locking）更新狀態，避免同一個任務被兩個 Worker 執行：
    - `UPDATE sys_pending_tasks SET status=1, version=version+1 WHERE id=? AND version=?`
        


### 狀態機

這是針對通用 Pending 表的狀態機流轉設計，以及在高併發環境下如何利用樂觀鎖保證任務不被重複執行的實作詳解。

## 狀態機設計 (State Machine)

狀態機的目標是清晰定義任務的生命週期，確保任務不會卡死或重複執行。建議使用整數（TINYINT）來表示狀態。

### 狀態定義
- 0 (PENDING - 待處理)
    
    任務剛建立，或者等待重試中。
- 1 (PROCESSING - 處理中)
    
    已被某個 Worker 鎖定並正在執行，此時其他 Worker 不可見或不可搶佔。
- 2 (SUCCESS - 成功)
    
    任務執行完畢且成功，屬於終態（Terminal State）。
- 3 (FAIL_RETRY - 失敗待重試)
    
    執行發生錯誤，但未達最大重試次數。系統稍後會將其重置為 PENDING 或直接在下次掃描時處理。
- 4 (FAIL_TERMINAL - 永久失敗)
    
    超過最大重試次數仍然失敗，不再自動重試，需人工介入。屬於終態。
    

### 狀態流轉圖
- 初始路徑
    PENDING → PROCESSING
- 成功路徑
    PROCESSING → SUCCESS
- 可重試失敗路徑
    PROCESSING → FAIL_RETRY
    - _注意：通常會有一個排程將 `FAIL_RETRY` 且 `next_run_time < NOW()` 的任務改回 `PENDING`，或者 Worker 直接查詢 `PENDING + FAIL_RETRY` 的任務。_
- 永久失敗路徑
    PROCESSING → FAIL_TERMINAL
    
---

## 處理任務 (由各服務實作)


### 實作範例 (SQL 與 邏輯)

流程分為三個步驟：**取出任務**、**搶佔任務（加鎖）**、**回寫結果**。

### 第一步：取出候選任務 (Fetch)

Worker 從資料庫中「讀取」一批可以執行的任務。這裡只是讀取，還沒鎖定。

SQL

```
SELECT id, biz_type, payload, version 
FROM sys_pending_tasks 
WHERE status IN ('PENDING', 'FAIL_RETRY')
  AND next_run_time <= NOW() 
ORDER BY priority ASC 
LIMIT 10;
```
- _假設我們取到了 ID=100 的任務，當前的 `version` 為 5。_
    
### 第二步：搶佔任務 (Claim with Optimistic Lock)

Worker 嘗試將狀態改為「處理中 (1)」。這裡最關鍵的是 `WHERE` 條件中必須包含 **查出來的 version**。

SQL

```
UPDATE sys_pending_tasks 
SET 
    status = 'PROCESSING',           -- 改為處理中
    version = version + 1 -- 版本號遞增
WHERE 
    id = 100 
    AND version = {version};      -- 關鍵：只有版本號沒變才更新
```
- 程式邏輯判斷：
    
    執行這條 SQL 後，檢查 Affected Rows (受影響行數)。
    - **如果 Affected Rows = 1**：搶佔成功！該 Worker 獲得執行權，開始執行業務邏輯。
    - **如果 Affected Rows = 0**：搶佔失敗。代表在「讀取」和「更新」的微小時間差內，另一個 Worker 已經搶先拿走並更新了 version。當前 Worker 應放棄此任務，繼續處理下一個。
        

### 第三步：處理任務

根據
biz_type 
biz_key
payload

處理任務邏輯


### 第四步：任務執行結束回寫 (Finalize)

業務邏輯執行完畢後，根據結果更新狀態。此時不需要再用樂觀鎖，因為該任務已經被當前 Worker 標記為 `status=1`，其他 Worker 不會碰到。
- **情境 A：執行成功**

```
UPDATE sys_pending_tasks 
SET 
    status = 'SUCCESS',           -- SUCCESS
    result_msg = 'OK',
    updated_at = NOW()
WHERE id = 100  AND version = {version};     
```
- **情境 B：執行失敗 (需要重試)**
    - 計算下一次執行時間（例如：當前時間 + 5分鐘）。
    - 重試次數 + 1。
        
```
UPDATE sys_pending_tasks 
SET 
    status = 'FAIL_RETRY', -- (或直接設回 PENDING)
    retry_count = retry_count + 1,
    next_run_time = DATE_ADD(NOW(), INTERVAL {nextRuntTime} MINUTE),
    result_msg = {error msg},
    updated_at = NOW()
WHERE id = 100  AND version = {version};      
```
- **情境 C：執行失敗 (達到最大重試次數)**
    
```
UPDATE sys_pending_tasks 
SET 
    status = 'FAIL_TERMINAL',
    result_msg = 'Max retries exceeded: {error msg}',
    updated_at = NOW()
WHERE id = 100  AND version = 5;     
```

### 綜合建議
- 索引配合
    樂觀鎖的 UPDATE 語句務必走索引（Primary Key id），否則可能會導致 Table Lock，反而降低效能。
    

# 救援任務

當 Worker 在執行狀態 `1 (PROCESSING)` 時崩潰（OOM、斷電、重啟、程式 Bug），資料庫中的狀態會一直卡在 `1`，導致其他 Worker 認為它還在跑而不敢碰它。


### 解法一：超時檢測機制 (Visibility Timeout) —— 最推薦，最簡單

這是最通用的做法。假設一個任務正常執行不應該超過 10 分鐘，如果一個任務卡在 `status=1` 且 `updated_at` 停留在 10 分鐘前，我們就判定它所在的 Worker 已經掛了。

#### 實作邏輯

你需要一個獨立的 **Monitor (監控程式)** 或 **Rescue Job (救援排程)**，每隔一段時間（例如 1 分鐘）執行一次「救援行動」。

#### 救援 SQL 範例

我們將這些「過期」的處理中任務，視為一次失敗，並打回重試隊列。

SQL

```
UPDATE sys_pending_tasks
SET 
    status = 'FAIL_RETRY', -- 改為 FAIL_RETRY (失敗待重試)
    retry_count = retry_count + 1, -- 消耗一次重試機會
    result_msg = 'Task timed out (Worker crashed?)',
    version = version + 1, -- 重要：增加版本號
    next_run_time = NOW(), -- 立即或稍後重試
    updated_at = NOW()
WHERE 
    status = 'PROCESSING' -- 找出處於「處理中」的任務
    AND updated_at <= DATE_SUB(NOW(), INTERVAL 10 MINUTE); -- 條件：超過 10 分鐘沒更新
```

**設計細節：**

1. 為什麼要算作一次重試 (retry_count + 1)？
    
    因為 Worker 崩潰可能是由該任務的某些資料導致的（例如「毒丸數據 Poison Pill」，一處理就 Crash）。如果不增加重試計數，這個任務可能會無限導致 Worker 崩潰，陷入無窮迴圈。
    
2. 時間閾值設定：
    
    閾值（例如 10 分鐘）必須設定得比「正常最長執行時間」還要長很多，以免誤殺正在跑大數據的正常 Worker。
    

---

### 關鍵安全網：舊 Worker 復活了怎麼辦？

這就是我們上一輪提到的 **樂觀鎖 (Optimistic Lock / Version)** 發揮最大價值的地方。

**場景：**

1. Worker A 正在跑任務 #100（Version=5），因為網路卡頓或 GC 停頓（Stop-the-world）了 15 分鐘。
    
2. **救援排程** 發現超時，將任務 #100 重置為 `PENDING`，並將 **Version 更新為 6**。
    
3. Worker B 搶到了任務 #100（Version=6），開始執行。
    
4. 突然，Worker A 恢復了！它跑完了邏輯，準備提交成功結果：
    

SQL

```
-- Worker A 嘗試提交結果
UPDATE sys_pending_tasks 
SET status = 2, result_msg = 'OK' 
WHERE id = 100 AND version = 5; -- 這裡的 version 是 5
```

結果：

Affected Rows = 0。更新失敗。

結論：

Worker A 的結果會被資料庫擋掉，因為版本號已經變了。這完美防止了 Worker A 和 Worker B 同時修改數據或導致業務重複執行（當然，你的業務邏輯最好還是要具備冪等性 Idempotency）。

### 總結建議

1. **加上救援排程 (Rescue Job)**：這是必須的。定時 (Cron) 執行 `UPDATE ... WHERE status=1 AND updated_at < timeout`。
    
2. **設定合理的 Timeout**：通常設為 `預期執行時間 * 3` 左右。
    
3. **依賴 Version 鎖**：確保救援後的任務不會被「復活的殭屍」覆蓋。

# 數據歸檔 (Archiving)
    
    Pending 表的數據量應保持輕量。建議設計一個定時任務（CronJob），每天將 status = 2 (成功) 或 status = 4 (永久失敗) 的記錄搬移到 sys_pending_tasks_history 歷史表，並從主表中刪除，以維持索引效率。
- 分庫分表考量
    
    如果業務量極大，可以根據 biz_type 進行分表，或者直接使用 Redis (List/Stream) 或 Message Queue (Kafka/RabbitMQ) 來替代資料庫表。但在需要「交易一致性 (Transactional Outbox Pattern)」的場景下，上述的 DB Table 設計仍然是最佳解法。
    

此設計是否符合您目前的系統架構需求？若您使用的是特定的資料庫（如 PostgreSQL），我可以針對其特性（如 `JSONB` 索引或 `SKIP LOCKED`）進一步優化語法。
