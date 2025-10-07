package open.vincentf13.common.sping.session;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CookieConfig {

    /**
     * 僅在需要配置 cookie MaxAge 時開啟，
     * 因為 spring session 的 maxAge 只能在這裡設定
     *
     * 此配置會覆蓋 servlet的 cookie配置，所有配置都需要重新設定
     */
//    @Bean
//    CookieSerializer cookieSerializer() {
//        DefaultCookieSerializer s = new DefaultCookieSerializer();
//        s.setCookieName("SESSION");         // Spring Session 預設名
//        s.setUseHttpOnlyCookie(true);       // JS 不可讀 風險較低
//        s.setUseSecureCookie(true);         // 僅 HTTPS
//        s.setSameSite("None");              // 前後端跨域必須 None 同站改 Lax
//        // s.setDomainName("example.com");  // 只有需要跨子網域才設 否則保持 host-only
//        s.setCookiePath("/");
//        s.setCookieMaxAge(30 * 60);         // Cookie 存活與 session 逾時可不同
//        return s;
//    }
}