package open.vincentf13.sdk.infra.redis;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Distributed lock helper backed by Redisson's {@link RLock} implementation.
 */
public final class OpenRedissonLock {

    private static final OpenRedissonLock INSTANCE = new OpenRedissonLock();

    private static RedissonClient redissonClient;

    private OpenRedissonLock() {
    }

    public static void register(RedissonClient client) {
        redissonClient = Objects.requireNonNull(client, "redissonClient");
    }

    public static OpenRedissonLock getInstance() {
        return INSTANCE;
    }

    public static <T> T withLock(String key, Duration waitTime, Duration leaseTime, Supplier<T> work) {
        Objects.requireNonNull(work, "work");
        boolean locked = false;
        try {
            locked = tryLock(key, waitTime, leaseTime);
            if (!locked) {
                return null;
            }
            return work.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread interrupted while acquiring lock \"" + key + "\"", ex);
        } finally {
            if (locked) {
                unlock(key);
            }
        }
    }


    public static boolean tryLock(String key, Duration waitTime, Duration leaseTime) throws InterruptedException {
        Objects.requireNonNull(key, "key");
        RLock lock = client().getLock(key);
        long wait = waitTime == null ? 0 : waitTime.toMillis();
        long lease = leaseTime == null ? -1 : leaseTime.toMillis();
        if (wait <= 0) {
            return lease < 0 ? lock.tryLock() : lock.tryLock(0, lease, TimeUnit.MILLISECONDS);
        }
        if (lease < 0) {
            return lock.tryLock(wait, TimeUnit.MILLISECONDS);
        }
        return lock.tryLock(wait, lease, TimeUnit.MILLISECONDS);
    }

    public static void unlock(String key) {
        Objects.requireNonNull(key, "key");
        RLock lock = client().getLock(key);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    private static RedissonClient client() {
        if (redissonClient == null) {
            throw new IllegalStateException("OpenRedissonLock not initialized");
        }
        return redissonClient;
    }
}
