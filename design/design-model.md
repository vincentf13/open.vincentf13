# 設計模型設計指南（Design Model）

# model 定義
- **DTO（Data Transfer Object）**：對外 API 的入參與出參模型，無業務邏輯；在本專案一律使用 Java `record` 定義，以確保不可變與序列化友善。
- **Domain Model**：承載核心業務邏輯的實體、聚合與領域服務；允許方法、校驗與狀態轉換。
- **PO（Persistence Object）**：資料庫映射物件，與資料表欄位一一對應；只保留欄位與 JPA/MyBatis 等框架需要的標註。
- **Value Object（VO）**：具備不可變、由值識別的領域建模組件；放置在 `sdk-common/sdk-core` 的 `open.vincentf13.common.core.valueobject` 套件內供各服務共用。


### DTO
- 若尚未抽離共用模組，可暫時放於 `services/<service>/src/main/java/.../api/dto/`，後續再將穩定契約搬遷至對應的 `*-rest-api` 模組。
- 命名：`*Request`, `*Response`, `*DTO`；保持語意化。
- 使用 `record` 宣告欄位，若需額外方法可在 record 內補充靜態工廠或驗證邏輯。
- 允許使用 `@JsonProperty`、`@Schema` 等序列化／文件註解；避免注入框架專屬 Annotation（例如 JPA、MyBatis）。

### Domain Model
- 儲存位置：`services/<service>/src/main/java/.../domain/`。
- 以聚合為單位建立子套件：如 `domain/order`, `domain/user`。
- Entity 使用類別（`class`），需要不變條件時可搭配建構子或靜態工廠檢查。
- 領域服務命名為 `*DomainService` 或 `*Policy`，只暴露與領域相關的操作。
- 使用 `ValueObject` 表示複雜值型別，例如 `Money`, `OrderId`；從 `sdk-core` 中引用，減少重複實作。

### Value Object
- 放置在 `sdk-common/sdk-core/src/main/java/open/vincentf13/common/core/valueobject`。
- Value Object 應：
  - 具備不可變欄位與自我驗證（於建構子或 `of` 工廠方法中完成）。
  - 實作 `equals`/`hashCode`（建議使用 `record` 或覆寫方法）。
  - 提供對業務語意友善的行為（例：`Money.add(Money other)`、`OrderId.generate()`）。
- 共用的 Value Object 於此集中定義，服務僅透過導入 `sdk-core` 取得。

### PO（Persistence Object）
- 儲存位置：`services/<service>/src/main/java/.../repository/po/` 或 `.../persistence/po/`。
- 僅保留資料庫欄位與 ORM 標註（`@Entity`, `@Table`, `@Column` 等）。
- 允許有 `@Builder`、`@AllArgsConstructor` 等 Lombok Annotation，方便 ORM 或 Builder 使用。
- 不放業務邏輯，必要時可於 PO 內提供靜態轉換為 Domain 的方法，但推薦使用 MapStruct 維護轉換邏輯。

## 使用流程建議
1. Controller `implements *Api`，接收/回傳 DTO（record）。
2. Application/Domain 層將 DTO 轉為 Domain Model（使用 MapStruct 或顯式工廠）。
3. Domain Model 內部操作 Value Object 與領域邏輯，輸出結果。
4. Repository 保存或取得資料，並在 Domain ↔ PO 之間轉換。
5. 回傳結果時，再由 Domain 轉回 DTO（透過 MapStruct/Assembler）。

## MapStruct + API Interface 範例
以下示範 `Order` 聚合的轉換與 API 介面：

```java
// DTO：使用 record
public record OrderCreateRequest(
        String customerId,
        BigDecimal amount,
        String currency
) {}

public record OrderDetailResponse(
        String orderId,
        BigDecimal amount,
        String currency,
        String status
) {}
```

```java
// API Interface：Controller 與客戶端共享契約
@RequestMapping("/api/v1/orders")
public interface OrderApi {

    @PostMapping
    OrderDetailResponse create(@RequestBody OrderCreateRequest request);

    @GetMapping("/{orderId}")
    OrderDetailResponse get(@PathVariable String orderId);
}
```

```java
// Domain：使用 Value Object 與領域邏輯
public class Order {

    private final OrderId orderId;
    private final CustomerId customerId;
    private Money totalAmount;
    private OrderStatus status;

    private Order(OrderId orderId, CustomerId customerId, Money totalAmount, OrderStatus status) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.totalAmount = totalAmount;
        this.status = status;
    }

    public static Order create(CustomerId customerId, Money amount) {
        return new Order(OrderId.generate(), customerId, amount, OrderStatus.CREATED);
    }

    // getters / domain behaviors ...
}
```

```java
// PO：純粹映射資料庫欄位
@Entity
@Table(name = "orders")
public class OrderPO {

    @Id
    private String id;

    private String customerId;

    private BigDecimal amount;

    private String currency;

    private String status;

    // getters/setters/constructors ...
}
```

```java
// MapStruct 組合器
@Mapper(componentModel = "spring")
public interface OrderAssembler {

    @Mapping(target = "orderId", expression = "java(OrderId.generate())")
    @Mapping(target = "totalAmount", expression = "java(Money.of(request.amount(), request.currency()))")
    @Mapping(target = "status", constant = "CREATED")
    Order toDomain(OrderCreateRequest request);

    @InheritInverseConfiguration(name = "toDomain")
    @Mapping(target = "status", expression = "java(order.status().name())")
    OrderDetailResponse toResponse(Order order);

    @Mapping(target = "id", source = "orderId.value")
    @Mapping(target = "amount", source = "totalAmount.amount")
    @Mapping(target = "currency", source = "totalAmount.currency")
    @Mapping(target = "status", source = "status")
    OrderPO toPO(Order order);

    @InheritInverseConfiguration(name = "toPO")
    Order toDomain(OrderPO po);
}
```

### MapStruct 使用要點
- `componentModel = "spring"` 方便透過 Spring 依賴注入。
- 值物件欄位可透過 `expression` 或自訂 `@Mapping(qualifiedByName = ...)` 完成轉換。
- 若轉換邏輯變複雜，建議拆出 `@Mapper(uses = {...})` 輔助類別維護。
- 測試時建立 unit test 驗證 MapStruct 產生的 Mapper 是否符合預期，避免欄位漏轉。

## 實作 Checklist
- [ ] DTO 一律使用 record、並集中於共用 REST 契約模組。
- [ ] API Interface 定義於 `*-rest-api`，Controller 與客戶端共用。
- [ ] Domain 物件掌控業務規則與生命週期。
- [ ] Value Object 集中於 `sdk-core`，服務端僅引用不重複定義。
- [ ] PO 僅保留資料庫結構、轉換交給 MapStruct。
- [ ] MapStruct Mapper 寫對應測試，確保 DTO/Domain/PO 欄位同步。

透過上述規範，可在各服務維持統一的模型層設計與轉換策略，減少跨層邏輯混雜並提高共用程度。
