- 所有 DDL 的審計欄位 `created_by`/`updated_by` 由資料庫統一處理：INSERT 時同時寫入 created_by 與 updated_by，UPDATE 時更新 updated_by；應用程式層禁止手動賦值這兩欄。`created_at`/`updated_at` 同樣由 DB 預設或 trigger 管控，Domain/PO/Mapper 不得手動設值。
- 全域 MyBatis Enum TypeHandler 已將 Enum `name()` 寫入資料庫並自動還原，禁止在程式中手動轉字串或自建 enum↔字串映射。
- Repository:	
- Repository : 使用以下規定，不要建立其他方法，除非特殊需要提出討論。
	- findOne ，入參為 domain 內含給定查詢條件，在其此組成PO後，查詢 mabtis的  findBy(PO)，若返回超過一個則報錯，否則轉成domain返回
	- findBy ，入參為 domain 內含給定查詢條件，在其此組成PO後，查詢 mabtis的  findBy(PO)，轉成domain返回
	- getOrCreate，帳戶或快照等用戶資產，一律延遲建立。建立時 domain 負責初始化。
	- insertSelective，入參為domain物件，在其此組成PO後，執行mabtis的 insertSelective
	- upsertSelective，入參為domain物件，在其此組成PO後，執行mabtis的  upsertSelective
	- 一般的update，使用 updateSelecticeBy，
		- 第一個參數為 domain，為要更新的值，調用mybatis updateSelective，若值不為null 則更新。 
		- 第二及其他參數為 WHERE 條件 使用的參數。
		- 所有 updateSelecticeBy 使用同一方法，只是不同場景第二~第N個參數，可能部分帶null值。

- Mybatis Mapper
	- 優先使用 insertSelective / updateSelecticeBy / findBy(PO) 模板，避免重工。
		- Mybatis 的 updateSelecticeBy
			- 第一個參數為 domain，為要更新的值，若值不為null 則更新。 
			- 第二及其他參數為 WHERE 條件 使用的參數。
			- 所有 updateSelecticeBy 使用同一方法，只是不同場景第二~第N個參數，可能部分帶null值。
		- 特殊的 update 依場景，使用專用方法，方便了解具體改動的值
	- 所有xml內，不需要寫 resultMap 因為已經會自動轉換
	- 若已配置 `mybatis.type-aliases-package`，`parameterType` ,`resultType` 直接寫別名（例如 `PlatformBalancePO`），可省略全限定類名。




