package open.vincentf13.sdk.devtool.logging;

import lombok.RequiredArgsConstructor;
import open.vincentf13.sdk.auth.auth.PublicAPI;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/logging")
@RequiredArgsConstructor
public class LoggingController {

    private final LoggingSystem loggingSystem;
    private final ObjectProvider<SqlSessionFactory> sqlSessionFactoryProvider;

    /**
       動態切換服務日誌等級 (Debug/Info)。

       參數說明：
       <table>
         <thead>
           <tr>
             <th>參數</th>
             <th>類型</th>
             <th>必填</th>
             <th>作用說明</th>
           </tr>
         </thead>
         <tbody>
           <tr>
             <td>kafka</td>
             <td>Boolean</td>
             <td>否</td>
             <td>true=DEBUG, false=INFO (open.vincentf13.sdk.infra.kafka)</td>
           </tr>
           <tr>
             <td>mvc</td>
             <td>Boolean</td>
             <td>否</td>
             <td>true=DEBUG, false=INFO (open.vincentf13.sdk.spring.mvc.log)</td>
           </tr>
           <tr>
             <td>feign</td>
             <td>Boolean</td>
             <td>否</td>
             <td>true=DEBUG, false=INFO (feign.http)</td>
           </tr>
           <tr>
             <td>mybatis</td>
             <td>Boolean</td>
             <td>否</td>
             <td>true=DEBUG, false=INFO (Mapper packages)</td>
           </tr>
         </tbody>
       </table>

       API 請求範本 (.http):
       ### 切換 Kafka/MVC/Feign/MyBatis 全部為 DEBUG
       POST http://localhost:8080/api/logging
       Content-Type: application/json

       {
         "kafka": true,
         "mvc": true,
         "feign": true,
         "mybatis": true
       }

       ### 僅切換 Kafka 與 MyBatis 為 INFO
       POST http://localhost:8080/api/logging
       Content-Type: application/json

       {
         "kafka": false,
         "mybatis": false
       }
     */
    @PostMapping
    @PublicAPI
    public OpenApiResponse<Map<String, String>> updateLogging(@RequestBody LoggingToggleRequest request) {
        Map<String, String> updated = new LinkedHashMap<>();
        applyLevel("open.vincentf13.sdk.infra.kafka", request.kafka(), updated);
        applyLevel("open.vincentf13.sdk.spring.mvc.log", request.mvc(), updated);
        applyLevel("feign.http", request.feign(), updated);
        applyMybatisLevel(request.mybatis(), updated);
        return OpenApiResponse.success(updated);
    }

    private void applyLevel(String loggerName,
                            Boolean enabled,
                            Map<String, String> updated) {
        if (enabled == null) {
            return;
        }
        LogLevel level = enabled ? LogLevel.DEBUG : LogLevel.INFO;
        loggingSystem.setLogLevel(loggerName, level);
        updated.put(loggerName, level.name());
    }

    private void applyMybatisLevel(Boolean enabled,
                                   Map<String, String> updated) {
        if (enabled == null) {
            return;
        }
        LogLevel level = enabled ? LogLevel.DEBUG : LogLevel.INFO;
        Set<String> mapperPackages = new LinkedHashSet<>();
        sqlSessionFactoryProvider.stream().forEach(factory ->
                factory.getConfiguration().getMapperRegistry().getMappers().forEach(mapperClass -> {
                    String packageName = mapperClass.getPackageName();
                    if (StringUtils.hasText(packageName)) {
                        mapperPackages.add(packageName);
                    }
                })
        );
        if (mapperPackages.isEmpty()) {
            updated.put("open.vincentf13.mybatis.mapper-sql-logging", "no-mapper-packages");
            return;
        }
        for (String packageName : mapperPackages) {
            loggingSystem.setLogLevel(packageName, level);
            updated.put(packageName, level.name());
        }
    }

    public record LoggingToggleRequest(
            Boolean kafka,
            Boolean mvc,
            Boolean feign,
            Boolean mybatis
    ) {
    }
}
