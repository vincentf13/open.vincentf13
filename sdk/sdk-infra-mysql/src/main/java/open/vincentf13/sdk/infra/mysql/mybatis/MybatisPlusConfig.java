package open.vincentf13.sdk.infra.mysql.mybatis;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

  @Bean
  public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    // 防攻性攔截器：防止全表更新與刪除，作為資料庫安全的最後一道防線，避免人為失誤導致災難性數據損失
    interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
    // 分頁攔截器：自動處理實體類的物理分頁，優化大數據量查詢效能
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
    return interceptor;
  }
}
