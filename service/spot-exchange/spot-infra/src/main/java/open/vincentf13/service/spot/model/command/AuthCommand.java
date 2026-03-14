package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.sbe.AuthDecoder;
import open.vincentf13.service.spot.sbe.AuthEncoder;
import open.vincentf13.service.spot.sbe.MessageHeaderDecoder;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * 用戶認證指令
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuthCommand extends AbstractSbeModel {
    public AuthDecoder decode() {
        ThreadContext ctx = ThreadContext.get();
        DirectBuffer buffer = wrapStore(pointBytesStore);
        ctx.getHeaderDecoder().wrap(buffer, 0);
        MessageHeaderDecoder header = ctx.getHeaderDecoder();
        return ctx.getAuthDecoder().wrap(buffer, HEADER_SIZE, header.blockLength(), header.version());
    }

    public void encode(long timestamp, long userId) {
        ThreadContext ctx = ThreadContext.get();
        MutableDirectBuffer buffer = ctx.getScratchBuffer().wrapForWrite();
        AuthEncoder encoder = ctx.getAuthEncoder();
        wrapHeader(buffer, AuthEncoder.TEMPLATE_ID, AuthEncoder.BLOCK_LENGTH, AuthEncoder.SCHEMA_ID, AuthEncoder.SCHEMA_VERSION);
        encoder.wrap(buffer, HEADER_SIZE).timestamp(timestamp).userId(userId);
        fillFromScratch(HEADER_SIZE + encoder.encodedLength());
    }
}
