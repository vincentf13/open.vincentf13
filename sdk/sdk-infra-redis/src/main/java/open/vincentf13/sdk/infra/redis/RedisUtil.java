package open.vincentf13.sdk.infra.redis;

import java.time.Duration;
import java.util.Map;
import java.util.WeakHashMap;

final class RedisUtil {

    private static final Map<String, Object> INTERN = new WeakHashMap<>();

    private RedisUtil() {
    }

    static synchronized Object intern(String s) {
        return INTERN.computeIfAbsent(s, k -> new Object());
    }

    static Duration withJitter(Duration ttl) {
        long jitterMs = (long) (Math.random() * Math.max(1, Duration.ofSeconds(30).toMillis()));
        return ttl.plus(Duration.ofMillis(jitterMs));
    }
}
