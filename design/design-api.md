- **API Interface**：定義 REST 端點的介面，Controller 與 Feign/WebClient 客戶端可共用，確保契約一致。




### 共用 REST 契約模組
- 儲存位置：`sdk-common/sdk-service-<domain>/<module>/<module>-rest-api/src/main/java/.../api/`。
- 在同一模組下集中定義：
	- DTO（`record`）。
	- API Interface（`*Api`）。
	- OpenAPI 定義與相關常數。
- 建議套件路徑：`.../api/dto/`、`.../api/contract/`、`.../api/constant/` 依需求拆分。
- Controller 實作與外部 client 皆依賴這個模組，並實作API Interface，統一宣告契約。
	- Controller `implements *Api`，確保方法簽章與註解同步。
	- Feign/WebClient 客戶端 `extends *Api` ；集中於 `sdk-service-<domain>-*-rest-client`。
