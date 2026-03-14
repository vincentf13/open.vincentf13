package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.AuthDecoder;
import open.vincentf13.service.spot.sbe.AuthEncoder;
import org.agrona.DirectBuffer;

/** 用戶認證指令 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuthCommand extends AbstractSbeModel {
    private final AuthEncoder encoder = new AuthEncoder();
    private final AuthDecoder decoder = new AuthDecoder();

    @Override
    protected void wrapDecoder(DirectBuffer buffer, int offset, int blockLength, int version) {
        decoder.wrap(buffer, offset, blockLength, version);
    }

    public void encode(long timestamp, long userId) {
        encoder.wrap(preEncode(AuthEncoder.TEMPLATE_ID, AuthEncoder.BLOCK_LENGTH, AuthEncoder.SCHEMA_ID, AuthEncoder.SCHEMA_VERSION), HEADER_SIZE)
                .timestamp(timestamp).userId(userId);
        postEncode(encoder.encodedLength());
    }

    public long getTimestamp() { return decoder.timestamp(); }
    public long getUserId() { return decoder.userId(); }
}
