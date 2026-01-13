package open.vincentf13.sdk.infra.mysql.config;

import open.vincentf13.sdk.core.config.AbstractYamlDefaultsEnvironmentPostProcessor;

public class SdkInfraMysqlDefaultsEnvironmentPostProcessor
    extends AbstractYamlDefaultsEnvironmentPostProcessor {

  @Override
  protected String getResourceLocation() {
    return "sdk-infra-mysql-defaults.yaml";
  }
}
