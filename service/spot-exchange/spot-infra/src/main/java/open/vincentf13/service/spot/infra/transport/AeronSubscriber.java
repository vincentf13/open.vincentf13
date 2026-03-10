package open.vincentf13.service.spot.infra.transport;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;

/** 
  Aeron 訊息訂閱者封裝
 */
public class AeronSubscriber {
    private final Subscription subscription;

    public AeronSubscriber(Aeron aeron, String channel, int streamId) {
        this.subscription = aeron.addSubscription(channel, streamId);
    }

    public int poll(FragmentHandler fragmentHandler, int fragmentLimit) {
        return subscription.poll(fragmentHandler, fragmentLimit);
    }

    public void close() {
        subscription.close();
    }
}
