package open.vincentf13.sdk.spring.cloud.gateway.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "open.vincentf13.security.gateway")
public class JwtProperties {
    
    private boolean enabled = true;
    private List<String> permitPaths = new ArrayList<>();
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public List<String> getPermitPaths() {
        return permitPaths;
    }
    
    public void setPermitPaths(List<String> permitPaths) {
        this.permitPaths = permitPaths == null ? new ArrayList<>() : new ArrayList<>(permitPaths);
    }
}
