# Project Handbook

## Repo Layout
- Root `pom.xml` 聚合 `sdk`、`service`；指令從根目錄執行。
- `service/<module>/`: 業務微服務，含專屬 `pom.xml` 與 `Dockerfile`。

## Build & Run
- 政策：Agent 預設**不執行 Maven 指令與測試**；必要時僅執行 `mvn clean compile`。
- 本地啟動：`mvn -pl service/<service> spring-boot:run`。

## General Coding
- **基礎規範**: Java UTF-8, 4-space; PascalCase 類名, camelCase 成員, UPPER_SNAKE 常數; 偏好建構子注入。
- **簡約原則**: Intent-Centric 風格，直接使用入參或欄位，避免多餘的 `null` 判斷、包裝或暫存變數；移除所有 `System.out`。
    - **例外**: 若入參或欄位操作後，需要宣告一個區域變數以說明其運算意義並增加可讀性（例如：說明堆外內存位址計算過程），則應使用具備明確語義的變數名稱。

## 驗證與測試 (受命執行時)
### 1. 結構與風格
- **1:1 結構**: 每個測試類別僅含一個測試方法，整合所有場景；
- 
### 2. 路徑設計技術
- **等價類劃分 (EP)**: 針對數值範圍、字串長度/內容、集合列舉、容量與檔案屬性，各劃分一組「有效」與「無效」代表值，以最少案例覆蓋最大邏輯。
- **邊界值分析 (BVA)**: 鎖定範圍邊緣點及其鄰近值（如 $1 \leq x \leq 100 \to 0, 1, 2, 99, 100, 101$）。
- **負向測試**: 必須包含 `null`/空值、格式錯誤、超載或模擬外部服務異常的「預期失敗」案例。

### 3. LeetCode 題目規範
- **套件結構**: 實作時應根據題目困難度 (Easy, Medium, Hard) 區分套件，路徑格式為 `open.vincentf13.sdk.leetcode.<difficulty>.<category>`。
- **用例優先級**: 撰寫單元測試時，優先完整實作 LeetCode 題目提供的官方範例 (Example Cases)；確保官方用例全數通過後，再依據邊界條件與等價類分析補充自訂測試用例。

## Exchange Rules (CQRS & Event)
- **嚴格 CQRS**: `*QueryService` (讀) 與 `*CommandService` (寫) 分離；Controller 實作 `sdk-contract` OpenAPI 介面。
- **Kafka**: 僅在處理成功後手動 Ack；禁止在 `finally` 中強制 Ack。
- **併發控制**: 取得實體後以版本號 (Version) 執行樂觀鎖更新，確保冪等。

## Agent Notes
- 回覆最終總結一律使用中文。
- 臨時輔助腳本統一存放於 `script/ai-tools/`。
- 除非使用者明確要求，否則不撰寫測試碼；不主動提交 (Commit) 除非獲得指令。
