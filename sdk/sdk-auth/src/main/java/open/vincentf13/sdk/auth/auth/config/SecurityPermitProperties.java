package open.vincentf13.sdk.auth.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ConfigurationProperties(prefix = "security")
public class SecurityPermitProperties {

    private List<String> permitPaths = new ArrayList<>();

    public List<String> getPermitPaths() {
        return Collections.unmodifiableList(permitPaths);
    }

    public void setPermitPaths(List<String> permitPaths) {
        this.permitPaths = permitPaths == null ? new ArrayList<>() : new ArrayList<>(permitPaths);
    }
}
