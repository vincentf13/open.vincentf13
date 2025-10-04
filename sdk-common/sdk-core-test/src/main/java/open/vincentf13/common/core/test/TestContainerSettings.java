package open.vincentf13.common.core.test;

import java.util.Locale;

/**
 * 控制是否啟用測試容器的組態：
 * <ul>
 *   <li>全域旗標：system property {@code sdk.core.testcontainers.enabled} 或環境變數 {@code SDK_CORE_TESTCONTAINERS_ENABLED}</li>
 *   <li>服務別旗標：system property {@code sdk.core.testcontainers.<name>.enabled}</li>
 * </ul>
 * 任一旗標設為 {@code false/0/off/no} 即視為停用；未設定則預設啟用。
 */
final class TestContainerSettings {

    private static final String GLOBAL_PROPERTY = "sdk.core.testcontainers.enabled";
    private static final String GLOBAL_ENV = "SDK_CORE_TESTCONTAINERS_ENABLED";

    private TestContainerSettings() {
    }

    static boolean mysqlEnabled() {
        return isEnabled("mysql");
    }

    static boolean redisEnabled() {
        return isEnabled("redis");
    }

    static boolean kafkaEnabled() {
        return isEnabled("kafka");
    }

    private static boolean isEnabled(String name) {
        Boolean specific = readFlag(propertyKey(name), envKey(name));
        if (specific != null) {
            return specific;
        }
        Boolean global = readFlag(GLOBAL_PROPERTY, GLOBAL_ENV);
        return global != null ? global : true;
    }

    private static String propertyKey(String name) {
        return "sdk.core.testcontainers." + name + ".enabled";
    }

    private static String envKey(String name) {
        return "SDK_CORE_TESTCONTAINERS_" + name.toUpperCase(Locale.ROOT) + "_ENABLED";
    }

    private static Boolean readFlag(String propertyKey, String envKey) {
        String value = System.getProperty(propertyKey);
        if (isBlank(value)) {
            value = System.getenv(envKey);
        }
        if (isBlank(value)) {
            return null;
        }
        return parseBoolean(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean parseBoolean(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "true":
            case "1":
            case "yes":
            case "on":
                return true;
            case "false":
            case "0":
            case "no":
            case "off":
                return false;
            default:
                return Boolean.parseBoolean(normalized);
        }
    }
}

