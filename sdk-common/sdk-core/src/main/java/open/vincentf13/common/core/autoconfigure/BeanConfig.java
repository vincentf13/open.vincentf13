package open.vincentf13.common.core.autoconfigure;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import open.vincentf13.common.core.jackson.JacksonUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
public class BeanConfig {

    @Bean
    public JacksonUtils jacksonUtils(ObjectMapper objectMapper) {
        return new JacksonUtils(objectMapper);
    }


    // 統一使用此配置，避免其他套件內的 ObjectMapper 影響。 例如：Spring MVC 取錯ObjectMapper，導致沒使用到此統一配置
    @Primary
    @Bean(name = "jsonMapper")
    public ObjectMapper jsonMapper() {
        return JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)     // 反序列化忽略未知欄位，防禦外部欄位變動
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)              // 輸出空物件{}時，不報錯
                .serializationInclusion(JsonInclude.Include.NON_NULL)                         // 略過 null，，縮小輸出
                .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)            // 屬性使用駝峰名稱，與後端一致方便維護
                .disable(SerializationFeature.INDENT_OUTPUT)                                  // 關閉縮排，生產建議關閉

                // 時間
                .addModule(new JavaTimeModule())                                              // 支持Java 8 時間序列化
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)                      // 預設情況下，Jackson 會將時間序列化輸出為「整數 timestamp」（例如 1696651325000）。 關閉此特性後，會改輸出為 ISO-8601 格式字串："2025-10-07T22:30:00"
                /**
                 * 默認Jackson 會依據TimeZone、Locale、DateFormat、ZonedDateTime，輸出不同日期格式。  例如：2025-10-07T22:30:00+0800、2025-10-07T22:30:00+08:00、Tue Oct 07 22:30:00 CST 2025、"2025-10-07T22:00:00+08:00[Asia/Taipei]"
                 * 使其統一輸出： RFC 3339 標準日期格式  "2025-10-07T22:30:00+08:00"
                 */
                .defaultDateFormat(new StdDateFormat().withColonInTimeZone(true))
                // 解析與輸出JSON時，時間戳由豪秒支持到奈秒
                .configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, true)
                .configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, true)


                // BigDecimal
                .configure(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN, true)       // 控制 BigDecimal 序列化時的輸出格式，避免以科學記號（Scientific Notation）表示金額或精確數值。
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)                    // 用途：解析浮點數時使用 BigDecimal 取代 Double。特別適合金融、會計、計量單位等需高精度的場景。

                // 效能
                // .addModule(new AfterburnerModule())                                        // 可選效能模組，在 JDK 17+ 收益有限且偶有相容性議題，除非量測證實再開。

                // 安全性
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)                        // 用途：偵測 JSON 物件中重複欄位名稱，若出現相同 key 會拋出例外。能防止潛在的資料覆蓋或攻擊手法（例如 JSON 混淆攻擊）。
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)                  // 用途：和 STRICT_DUPLICATE_DETECTION 類似，但作用於「讀取樹狀節點 (JsonNode)」階段。若同一層節點有重複 key，拋出錯誤。 確保 readTree() 也具備防重能力。
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)                       // 反序列化完成後若 JSON 中仍有未處理的多餘內容，直接報錯。啟用後解析時會拋出例外，避免資料格式不乾淨或混入多筆 JSON。
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)                   // 用途：若原始型別欄位（如 int, long, boolean）在 JSON 中為 null，則報錯。預設：關閉（Jackson 會自動轉為 0 或 false）。確保資料完整性，避免 null 轉 0 的隱性錯誤。
                .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)                     // 用途：禁止以整數數值解析 Enum。預設：關閉（允許用 Enum ordinal 值反序列化）。防止因 Enum 順序調整導致對應錯誤。
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)                       // 用途：序列化 Map 時依 key 排序輸出，確保 JSON 鍵值順序固定。確保輸出一致性，有利於快取比較與測試快照（snapshot testing）。
                .enable(MapperFeature.REQUIRE_SETTERS_FOR_GETTERS)                            // 用途：若某屬性只有 getter 無 setter，則反序列化報錯。避免一部分資料寫不進去仍默默忽略。
                .build();
    }
}
