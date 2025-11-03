package open.vincentf13.sdk.auth.server.config;

import open.vincentf13.sdk.core.config.AbstractYamlDefaultsEnvironmentPostProcessor;

public class SdkAuthServerDefaultsEnvironmentPostProcessor extends AbstractYamlDefaultsEnvironmentPostProcessor {

    @Override
    protected String getResourceLocation() {
        return "sdk-auth-server-defaults.yaml";
    }
}
