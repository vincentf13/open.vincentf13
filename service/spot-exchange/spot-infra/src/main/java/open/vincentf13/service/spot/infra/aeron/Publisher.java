package open.vincentf13.service.spot.infra.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.DirectBuffer;

public class Publisher implements AutoCloseable {
    private final Publication publication;

    public Publisher(Aeron aeron, String channel, int streamId) {
        this.publication = aeron.addPublication(channel, streamId);
    }

    public long tryPublish(DirectBuffer buffer, int offset, int length) {
        return publication.offer(buffer, offset, length);
    }

    @Override
    public void close() {
        if (publication != null) publication.close();
    }
}
