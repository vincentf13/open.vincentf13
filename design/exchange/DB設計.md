- 所有domain與PO，不對createdAt, updatedAt 設值，統一由 DB 在 insert update時賦值。
- 所有Enum，由typehandler處理，在DB寫入時自動call .name()轉為字串
- Repository : 提供以下方法
	- findOne ，入參為多個給定查詢條件，在其此組成PO後，查詢 mabtis的  findBy(PO)，若返回超過一個則報錯，否則轉成domain返回
	- findBy ，入參為多個給定查詢條件，在其此組成PO後，查詢 mabtis的  findBy(PO)，轉成domain返回
	- getOrCreate，帳戶或快照等用戶資產，一律延遲建立
	- insertSelective，入參為domain物件，在其此組成PO後，執行mabtis的 insertSelective
	- upsertSelective，入參為domain物件，在其此組成PO後，執行mabtis的  upsertSelective
	- update 依場景，使用專用方法，方便了解具體改動的值
	- 若有其他特殊方法需要提出討論。

- Mybatis Mapper
	- 優先使用 insertSelective  / findBy(PO) 模板，避免重工，update 依場景，使用專用方法，方便了解具體改動的值
	- 所有xml內，不需要寫 resultMap 因為已經會自動轉換
	- 若已配置 `mybatis.type-aliases-package`，`parameterType` ,`resultType` 直接寫別名（例如 `PlatformBalancePO`），可省略全限定類名。

