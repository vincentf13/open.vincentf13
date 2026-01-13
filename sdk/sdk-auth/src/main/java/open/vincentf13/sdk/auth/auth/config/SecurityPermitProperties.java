package open.vincentf13.sdk.auth.auth.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
