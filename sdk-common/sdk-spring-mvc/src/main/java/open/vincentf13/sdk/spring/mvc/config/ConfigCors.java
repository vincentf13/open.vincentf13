package open.vincentf13.sdk.spring.mvc.config;

import open.vincentf13.sdk.core.OpenConstant;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ConfigCors implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")                                                              // 套用到所有 MVC 路徑。可改成特定 API 前綴（例 /api/**）縮小範圍。
                .allowedOriginPatterns("http://127.0.0.1:*", "http://localhost:*", "http://*.local", "*") // 允許任何網域發起跨域請求，回覆 Access-Control-Allow-Origin:
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")                       // 允許的 HTTP 方法
                .allowedHeaders("*")                                                                      // 允許前端在跨域請求中攜帶的自訂請求標頭。若要全開可用 *（與 allow-credentials 不衝突）。
                .exposedHeaders(OpenConstant.REQUEST_ID_HEADER, OpenConstant.TRACE_ID_HEADER, "Content-Disposition")                      // 回傳 Access-Control-Expose-Headers。允許前端 JS 讀取這些回應標頭，例如取得檔名（Content-Disposition）或追蹤 ID。 "*" 在帶憑證時無效。任何網域都讀不到非 safelisted 的回應標頭，必須改成逐一列出要暴露的標頭。
                .allowCredentials(true)                                                                   // 回傳 Access-Control-Allow-Credentials: true。允許跨域時夾帶 Cookie 或憑證。同時必須回覆具體 Access-Control-Allow-Origin，不能是 *；前端需設 credentials: 'include'。
                .maxAge(3600);                                                                            // 回傳 Access-Control-Max-Age: 86400。瀏覽器可快取預檢結果 86400 秒以減少 OPTIONS。實際快取時長可能受瀏覽器上限限制。
    }
}
