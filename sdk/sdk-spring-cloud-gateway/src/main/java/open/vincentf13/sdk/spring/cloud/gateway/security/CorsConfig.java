package open.vincentf13.sdk.spring.cloud.gateway.security;

import open.vincentf13.sdk.core.OpenConstant;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class CorsConfig {
    
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 套用到所有 MVC 路徑。可改成特定 API 前綴（例 /api/**）縮小範圍。
        config.setAllowedOriginPatterns(Arrays.asList("http://127.0.0.1:*", "http://localhost:*", "http://*.local", "*")); // 允許任何網域發起跨域請求，回覆 Access-Control-Allow-Origin:
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")); // 允許的 HTTP 方法
        config.setAllowedHeaders(Collections.singletonList("*")); // 允許前端在跨域請求中攜帶的自訂請求標頭。若要全開可用 *（與 allow-credentials 不衝突）。
        config.setExposedHeaders(Arrays.asList(OpenConstant.HttpHeader.REQUEST_ID.value(), OpenConstant.HttpHeader.TRACE_ID.value(), "Content-Disposition")); // 回傳 Access-Control-Expose-Headers。允許前端 JS 讀取這些回應標頭，例如取得檔名（Content-Disposition）或追蹤 ID。 "*" 在帶憑證時無效。任何網域都讀不到非 safelisted 的回應標頭，必須改成逐一列出要暴露的標頭。
        config.setAllowCredentials(true); // 回傳 Access-Control-Allow-Credentials: true。允許跨域時夾帶 Cookie 或憑證。同時必須回覆具體 Access-Control-Allow-Origin，不能是 *；前端需設 credentials: 'include'。
        config.setMaxAge(3600L); // 回傳 Access-Control-Max-Age: 86400。瀏覽器可快取預檢結果 86400 秒以減少 OPTIONS。實際快取時長可能受瀏覽器上限限制。
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        
        return new CorsWebFilter(source);
    }
}