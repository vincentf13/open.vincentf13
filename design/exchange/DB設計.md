- 所有domain與PO，不對createdAt, updatedAt 設值，統一由 DB 在 insert update時賦值。
- 所有Enum，由typehandler處理，在DB寫入時自動call .name()轉為字串
- Repository : 提供以下方法
	- findOne ，入參為多個給定查詢條件，在其此組成PO後，查詢 mabtis的  findBy(PO)，若超過一個報錯，否則轉成domain返回
	- findBy ，入參為多個給定查詢條件，在其此組成PO後，查詢 mabtis的  findBy(PO)，轉成domain返回
	- insertSelective，入參為domain物件，在其此組成PO後，執行mabtis的 insertSelective
	- upsertSelective，入參為domain物件，在其此組成PO後，執行mabtis的  upsertSelective
	- updateSelectiveBy，第一個入參domain 物件為要更新的值，其他參數是update的WHERE條件的參數，有幾個where條件就要有幾個where參數，調用 mybatis 的 updateSelective，因為updateSelective。
	- 若有其他特殊方法需要提出討論。
- Mybatis Mapper
	- 優先使用 insertSelective / updateSelective / findBy(PO) 模板，避免重工
	- 所有xml內，如無必要，不需要寫 resultMap 因為已經會自動轉換


- 帳戶或快照等用戶資產，一律延遲建立，讀取時使用 `getOrCreate` + 樂觀鎖的方式；若不存在就建一筆新的帳戶/快照。例如：
```
@Transactional
public Account getOrCreate(long userId, String asset) {
    return accountRepository.findByUserIdAndAsset(userId, asset)
        .orElseGet(() -> accountRepository.save(
            Account.create(userId, asset)
        ));
}
```