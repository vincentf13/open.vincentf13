package open.vincentf13.sdk.auth.jwt.config;

import open.vincentf13.sdk.core.config.AbstractYamlDefaultsEnvironmentPostProcessor;

public class SdkAuthJwtDefaultsEnvironmentPostProcessor
    extends AbstractYamlDefaultsEnvironmentPostProcessor {

  @Override
  protected String getResourceLocation() {
    return "sdk-auth-jwt-defaults.yaml";
  }
}
