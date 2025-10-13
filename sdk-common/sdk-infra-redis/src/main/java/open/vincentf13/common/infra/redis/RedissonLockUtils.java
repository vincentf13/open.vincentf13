package open.vincentf13.common.infra.redis;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Distributed lock helper backed by Redisson's {@link RLock} implementation.
 */
public class RedissonLockUtils {

    private final RedissonClient redissonClient;

    public RedissonLockUtils(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public <T> T withLock(String key, Duration waitTime, Duration leaseTime, Supplier<T> work) {
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


    public boolean tryLock(String key, Duration waitTime, Duration leaseTime) throws InterruptedException {
        Objects.requireNonNull(key, "key");
        RLock lock = redissonClient.getLock(key);
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

    public void unlock(String key) {
        Objects.requireNonNull(key, "key");
        RLock lock = redissonClient.getLock(key);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
