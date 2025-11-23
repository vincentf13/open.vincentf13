package open.vincentf13.sdk.spring.cloud.openfeign.interceptor.apikey;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.api-key.client")
public class FeignApiKeyProperties {

    /** 是否啟用 Feign 請求自動攜帶 API Key */
    private boolean enabled = false;

    /** API Key 的實際值 */
    private String value;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
