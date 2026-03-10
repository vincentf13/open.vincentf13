package open.vincentf13.service.spot.infra.aeron;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;

public class Subscriber implements AutoCloseable {
    private final Subscription subscription;
    
    public Subscriber(Aeron aeron,
                      String channel,
                      int streamId) {
        this.subscription = aeron.addSubscription(channel, streamId);
    }
    
    public int poll(FragmentHandler handler,
                    int fragmentLimit) {
        return subscription.poll(handler, fragmentLimit);
    }
    
    @Override
    public void close() {
        if (subscription != null)
            subscription.close();
    }
}
