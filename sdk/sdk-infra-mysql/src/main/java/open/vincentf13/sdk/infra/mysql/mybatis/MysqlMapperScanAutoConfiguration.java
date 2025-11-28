package open.vincentf13.sdk.infra.mysql.mybatis;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 自動配置 MyBatis Mapper 掃描，避免各服務重複宣告。
 */
@AutoConfiguration
@ConditionalOnClass(MapperScan.class)
@MapperScan(basePackages = {
        "${open.vincentf13.mybatis.mapper-base-packages:open.vincentf13.exchange}",
        "open.vincentf13.sdk.infra.mysql"
})
public class MysqlMapperScanAutoConfiguration {
}
