package open.vincentf13.service.spot.model.command;

import open.vincentf13.service.spot.sbe.AuthDecoder;
import org.agrona.DirectBuffer;

/** AUTH 指令解碼器 (Flyweight) */
public class AuthCommand extends AbstractSbeModel {
    private final AuthDecoder decoder = new AuthDecoder();

    @Override protected void decoderReWrap(DirectBuffer buffer, int offset, int blockLength, int version) {
        decoder.wrap(buffer, offset, blockLength, version);
    }

    public long getUserId() { return decoder.userId(); }
    public long getTimestamp() { return decoder.timestamp(); }
}
