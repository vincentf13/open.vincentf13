package open.vincentf13.sdk.core.config;

public class SdkCoreDefaultsEnvironmentPostProcessor extends AbstractYamlDefaultsEnvironmentPostProcessor {
    
    @Override
    protected String getResourceLocation() {
        return "sdk-core-defaults.yaml";
    }
}
