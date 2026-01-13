package open.vincentf13.sdk.infra.redis;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import open.vincentf13.sdk.core.log.OpenLog;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/** Distributed lock helper backed by Redisson's {@link RLock} implementation. */
public final class OpenRedissonLock {

  private static final OpenRedissonLock INSTANCE = new OpenRedissonLock();

  private static RedissonClient redissonClient;

  private OpenRedissonLock() {}

  public static void register(RedissonClient client) {
    redissonClient = Objects.requireNonNull(client, "redissonClient");
  }

  public static OpenRedissonLock getInstance() {
    return INSTANCE;
  }

  /*
   * Acquire lock and execute business logic, releasing after completion.
   * Example:
   *   OpenRedissonLock.withLock("order:123", Duration.ofSeconds(1), Duration.ofSeconds(30), () -> {
   *       processOrder();
   *       return Boolean.TRUE;
   *   });
   * waitTime：等待鎖的期限，超過立即返回 false；null 表示立即嘗試。實際是透過 Redisson 向 Redis 發出阻塞式命令等待，而非 busy waiting。
   * leaseTime：鎖的租期，時間到自動釋放；null 表示不自動釋放；Redisson 會在 leaseTime 到期前啟動 watchdog 自動延長防止死鎖，合理時間請依業務執行長度估算。
   */
  public static <T> T withLock(
      String key, Duration waitTime, Duration leaseTime, Supplier<T> work) {
    Objects.requireNonNull(work, "work");
    boolean locked = false;
    try {
      locked = tryLock(key, waitTime, leaseTime);
      if (!locked) {
        return null;
      }
      return work.get();
    } catch (InterruptedException ex) {
      // 重新補上中斷標記，使外部取得中斷信號
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Thread interrupted while acquiring lock \"" + key + "\"", ex);
    } finally {
      if (locked) {
        unlock(key);
      }
    }
  }

  /*
   * Try acquiring lock within waitTime and optional leaseTime.
   * Example:
   *   if (OpenRedissonLock.tryLock("counter", Duration.ofMillis(500), Duration.ofSeconds(5))) {
   *       try {
   *           incrementCounter();
   *       } finally {
   *           OpenRedissonLock.unlock("counter");
   *       }
   *   }
   * waitTime：等待鎖的期限，超過立即返回 false；null 表示立即嘗試。Redisson 將在 Redis 端進行阻塞/訂閱方式等待 (非 busy waiting)。
   * leaseTime：鎖的租期，時間到自動釋放；null 表示不自動釋放；Redisson 會在 leaseTime 到期前啟動 watchdog 自動延長防止死鎖，合理時間依業務長度估算。
   */
  public static boolean tryLock(String key, Duration waitTime, Duration leaseTime)
      throws InterruptedException {
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

  /*
   * Unlock only when held by current thread.
   */
  public static void unlock(String key) {
    Objects.requireNonNull(key, "key");
    RLock lock = client().getLock(key);
    try {
      lock.unlock();
    } catch (IllegalMonitorStateException ignore) {
      // 鎖已過期或非本執行緒持有
      OpenLog.warn(
          OpenRedisEvent.LOCK_UNLOCK_FAILED,
          "lockKey",
          lock.getName(),
          "threadId",
          Thread.currentThread().getId());
    }
  }

  private static RedissonClient client() {
    if (redissonClient == null) {
      throw new IllegalStateException("OpenRedissonLock not initialized");
    }
    return redissonClient;
  }
}
