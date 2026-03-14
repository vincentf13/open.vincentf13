package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.AuthDecoder;
import open.vincentf13.service.spot.sbe.AuthEncoder;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * 用戶認證指令
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuthCommand extends AbstractSbeModel {
    private final AuthEncoder encoder = new AuthEncoder();
    private final AuthDecoder decoder = new AuthDecoder();

    public AuthDecoder decode() {
        DirectBuffer buffer = wrapStore(pointBytesStore);
        headerDecoder.wrap(buffer, 0);
        return decoder.wrap(buffer, HEADER_SIZE, headerDecoder.blockLength(), headerDecoder.version());
    }

    public void encode(long timestamp, long userId) {
        MutableDirectBuffer buffer = encodeBuffer.wrapForWrite();
        wrapHeader(buffer, AuthEncoder.TEMPLATE_ID, AuthEncoder.BLOCK_LENGTH, AuthEncoder.SCHEMA_ID, AuthEncoder.SCHEMA_VERSION);
        encoder.wrap(buffer, HEADER_SIZE).timestamp(timestamp).userId(userId);
        fillFromEncodeBuffer(HEADER_SIZE + encoder.encodedLength());
    }
}
