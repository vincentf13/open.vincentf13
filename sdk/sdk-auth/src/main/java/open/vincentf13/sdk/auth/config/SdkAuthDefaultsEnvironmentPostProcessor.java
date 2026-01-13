package open.vincentf13.sdk.auth.config;

import open.vincentf13.sdk.core.config.AbstractYamlDefaultsEnvironmentPostProcessor;

public class SdkAuthDefaultsEnvironmentPostProcessor
    extends AbstractYamlDefaultsEnvironmentPostProcessor {

  @Override
  protected String getResourceLocation() {
    return "sdk-auth-defaults.yaml";
  }
}
