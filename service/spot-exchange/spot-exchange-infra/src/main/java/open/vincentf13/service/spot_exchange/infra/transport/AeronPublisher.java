package open.vincentf13.service.spot_exchange.infra.transport;

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

    /** 
      嘗試發送訊息
      @return Aeron 原始結果代碼 (可判斷 BACK_PRESSURED, NOT_CONNECTED 等)
     */
    public long tryPublish(DirectBuffer buffer, int offset, int length) {
        return publication.offer(buffer, offset, length);
    }

    public void close() {
        publication.close();
    }
}
