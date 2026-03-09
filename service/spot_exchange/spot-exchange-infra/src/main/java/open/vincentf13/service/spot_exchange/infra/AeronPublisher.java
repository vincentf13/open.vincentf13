package open.vincentf13.service.spot_exchange.infra;

import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 
  Aeron 訊息發布者封裝
 */
public class AeronPublisher {
    private static final Logger log = LoggerFactory.getLogger(AeronPublisher.class);
    private final Publication publication;

    public AeronPublisher(Aeron aeron, String channel, int streamId) {
        this.publication = aeron.addPublication(channel, streamId);
    }

    public boolean publish(DirectBuffer buffer, int offset, int length) {
        long result = publication.offer(buffer, offset, length);
        if (result > 0) return true;
        
        if (result == Publication.BACK_PRESSURED) {
            // 可以在此實作重試或丟棄策略
        } else if (result == Publication.NOT_CONNECTED) {
            log.warn("Aeron Publisher not connected: channel={}", publication.channel());
        }
        return false;
    }

    public void close() {
        publication.close();
    }
}
