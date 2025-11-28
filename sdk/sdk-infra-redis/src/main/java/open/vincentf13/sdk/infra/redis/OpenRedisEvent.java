package open.vincentf13.sdk.infra.redis;

import open.vincentf13.sdk.core.log.OpenEvent;

/**
 Redis infra events for logging.
 */
public enum OpenRedisEvent implements OpenEvent {
    
    /**
     Unable to unlock because lock already expired or held by other thread.
     */
    LOCK_UNLOCK_FAILED("redis.lock.unlock.failed", "Redisson unlock failed");
    
    private final String event;
    private final String message;
    
    OpenRedisEvent(String event,
                   String message) {
        this.event = event;
        this.message = message;
    }
    
    @Override
    public String event() {
        return event;
    }
    
    @Override
    public String message() {
        return message;
    }
}
