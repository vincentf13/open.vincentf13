package open.vincentf13.common.core.autoconfigure;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
public class ObjectMapperAutoConfiguration {

    @Bean(name = "jsonMapper")
    @ConditionalOnMissingBean(name = "jsonMapper")
    public ObjectMapper jsonMapper() {


        return JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)     // 反序列化忽略未知欄位
                .serializationInclusion(JsonInclude.Include.NON_NULL)                         // 略過 null
                .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)            // userName
                .addModule(new JavaTimeModule())                                              // Java 8 時間型別
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)                      // 日期輸出為字串
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)              // 空物件不報錯
                .disable(SerializationFeature.INDENT_OUTPUT)                                  // 關閉縮排
                // .addModule(new AfterburnerModule())                                        // 可選效能模組
                .build();
    }
}
