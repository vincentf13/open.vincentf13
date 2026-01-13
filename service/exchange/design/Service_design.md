- 編排與協調領域層來完成完整用例 (Use Case)。因應 CQRS，服務類須區分為 `XxxCommandService`（寫入/狀態變更）與
  `XxxQueryService`（讀取/查詢）。
- **目錄與命名**:
    - `services/<module>/src/main/java/.../service/` 下以聚合為單位拆分，寫路徑固定為 `{Aggregate}CommandService`，查詢路徑固定為
      `{Aggregate}QueryService`。
    - Controller 直接呼叫 Command 或 Query 服務，避免一個類同時處理讀寫，方便分層測試與權限控管。
- **核心任務**:
    - **Command Service**: 代表事務邊界，負責驗證上下文、建立/修改聚合、發布應用事件；方法預設 `@Transactional`，並可透過
      `OpenObjectMapper` 轉換 DTO 與聚合。
    - **Query Service**: 僅負責讀模型組裝與查詢優化，不持有交易狀態；可讀取資料庫、快取或查詢視圖並直接返回 DTO。
    - **用例協調**: Command/Query 服務都需協調多個 `Domain`/`Infra` 元件完成業務流程（例如註冊流程協調 `User` 聚合與
      `UserRepository`）。
    - **應用事件發布**: Command 服務在完成狀態變更後，將必要事件推送至 Outbox。
