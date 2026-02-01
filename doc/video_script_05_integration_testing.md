# 影片腳本：分散式交易系統的自動化整合測試

## 影片資訊
- **主題**: 自動化整合測試：驗證分散式環境下的複雜交易流程與帳戶核對
- **目標觀眾**: 技術面試官、後端工程師、QA 工程師
- **核心關鍵字**: Integration Testing, Distributed Transactions, Event-Driven, Race Conditions, Financial Accuracy
- **對應程式碼**: `service/exchange/exchange-test/src/test/java/open/vincentf13/exchange/test/TradeTest.java`

---

## 腳本大綱 (Outline)

1.  **開場 (Intro)**: 介紹測試目標 —— 確保在分散式架構下，每一筆交易的資金流動與倉位變化都是精確無誤的。
2.  **測試架構 (Test Architecture)**: 說明採用黑箱測試策略，模擬真實使用者透過 API 進行操作。
3.  **核心場景演示 (Core Scenarios)**:
    *   標準倉位生命週期 (開倉 -> 減倉 -> 增倉 -> 平倉)
    *   反手交易 (Flip Position)
    *   並發/競爭場景 (Concurrent/Race Condition)
4.  **深度解析 (Deep Dive)**: 檢視 `TradeTest.java` 中的關鍵邏輯與驗證點。
5.  **總結 (Conclusion)**: 強調這套測試如何保證系統的高可靠性與正確性。

---

## 詳細腳本內容

### 1. 開場 (Introduction)

**[畫面]:** 展示 IDE 中的專案結構，聚焦在 `service/exchange` 與 `exchange-test` 模組。

**[旁白]:**
"大家好，在這個影片中，我要介紹我是如何針對這個分散式交易所系統進行「自動化整合測試」。
在金融系統中，正確性是唯一的真理。特別是在一個由撮合引擎、訂單管理、倉位管理、風險控制和帳戶系統組成的微服務架構中，單元測試雖然重要，但不足以驗證跨服務的業務邏輯。
因此，我設計了一套完整的整合測試，專門用來驗證複雜的交易流程與資金對帳。"

### 2. 測試架構 (Test Architecture)

**[畫面]:** 展示 `TradeTest.java` 的 `@BeforeEach` 區塊，看到 `AuthClient.login` 和 `AccountClient.deposit`。

**[旁白]:**
"這套測試採用了黑箱測試的策略。測試程式扮演了「使用者」的角色。
我們看這裡，測試開始前，系統會初始化環境，自動註冊並登入使用者（例如 User A 和 User B），並為他們存入初始資金（例如 10,000 USDT）。
所有的操作都是透過模擬的 HTTP Client 發送到系統的 API Gateway，這完全模擬了真實世界的互動路徑。"

### 3. 核心場景演示 (Core Scenarios)

#### 3.1 標準倉位生命週期

**[畫面]:** 快速捲動到 `step1_OpenPosition` 到 `step4_ClosePosition` 的程式碼。

**[旁白]:**
"首先，我們驗證最基本的倉位生命週期。
腳本會依序執行：
1.  **開倉 (Open)**: User A 買入，建立多頭倉位。
2.  **減倉 (Reduce)**: 賣出部分持倉，驗證已實現損益 (Realized PnL) 是否正確結算。
3.  **增倉 (Increase)**: 再次買入，驗證平均入場價 (Average Entry Price) 的加權計算。
4.  **平倉 (Close)**: 完全賣出，驗證倉位歸零且保證金完全釋放。
在每一步驟後，測試都會自動發起「對帳」，比對「倉位服務」與「帳戶服務」的數據是否一致。"

#### 3.2 反手交易 (Flip Position)

**[畫面]:** 聚焦在 `step6_FlipPosition`。

**[旁白]:**
"接下來是更複雜的「反手」場景，也就是 Flip。
當使用者持有 5 個多單，卻下單賣出 10 個時，系統必須原子性地執行兩個動作：先平掉 5 個多單，再開啟 5 個空單。
這考驗著系統處理狀態轉換的準確性，任何計算錯誤都會導致資金不翼而飛。"

#### 3.3 並發與競爭條件 (Concurrency & Race Conditions)

**[畫面]:** 聚焦在 `step7_ConcurrentFlipPosition`，強調 "Flip Stealing Reserved Position" 的註解。

**[旁白]:**
"最精彩的部分在於我們如何測試「並發」場景。
在分散式系統中，訂單到達撮合引擎的順序與執行結果至關重要。
例如這個測試案例：User A 掛了兩筆單，一筆是用來「反手」的大單，另一筆是普通買單。
當這兩筆單幾乎同時成交時，系統會面臨「搶奪預留倉位」(Stealing Reserved Position) 的競爭條件。
我的測試確保了即使在這種極端情況下，系統的鎖定機制與狀態機也能正確處理，確保 User A 的最終倉位數量與資金餘額一分不差。"

### 4. 深度解析 (Deep Dive)

**[畫面]:** 打開 `TradeTest.java` 中的 `verifyPosition` 和 `verifyAccount` 方法。

**[旁白]:**
"讓我們看一眼驗證邏輯。
在 `verifyPosition` 中，我不僅僅是檢查倉位數量，還檢查了：
- **Entry Price**: 是否正確計算了加權平均。
- **Unrealized PnL**: 未實現損益是否隨市價正確浮動。
- **Margin Ratio**: 保證金率是否符合風險模型。

而在 `verifyAccount` 中，我們嚴格比對 Spot（現貨）與 Margin（保證金）帳戶的餘額。
任何因為手續費計算、資金費率或精確度截斷（Rounding）產生的誤差，都會導致測試失敗。這就是金融級別的測試標準。"

### 5. 總結 (Conclusion)

**[畫面]:** 展示測試執行的綠燈結果（可以是截圖或假裝執行）。

**[旁白]:**
"總結來說，這套自動化整合測試不僅是品質的守門員，更是系統重構與優化的安全網。它證明了我們的交易核心在面對複雜的撮合情境與並發交易時，依然能保持資料的高度一致性與正確性。
這就是我對「高可靠性交易系統」的實踐。"

---
