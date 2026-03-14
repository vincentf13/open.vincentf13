package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.sbe.DepositDecoder;
import open.vincentf13.service.spot.sbe.DepositEncoder;
import open.vincentf13.service.spot.sbe.MessageHeaderDecoder;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * 充值指令
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DepositCommand extends AbstractSbeModel {
    public DepositDecoder decode() {
        ThreadContext ctx = ThreadContext.get();
        DirectBuffer buffer = wrapStore(pointBytesStore);
        ctx.getHeaderDecoder().wrap(buffer, 0);
        MessageHeaderDecoder header = ctx.getHeaderDecoder();
        return ctx.getDepositDecoder().wrap(buffer, HEADER_SIZE, header.blockLength(), header.version());
    }

    public void encode(long timestamp, long userId, int assetId, long amount) {
        ThreadContext ctx = ThreadContext.get();
        MutableDirectBuffer buffer = ctx.getScratchBuffer().wrapForWrite();
        DepositEncoder encoder = ctx.getDepositEncoder();
        wrapHeader(buffer, DepositEncoder.TEMPLATE_ID, DepositEncoder.BLOCK_LENGTH, DepositEncoder.SCHEMA_ID, DepositEncoder.SCHEMA_VERSION);
        encoder.wrap(buffer, HEADER_SIZE).timestamp(timestamp).userId(userId).assetId(assetId).amount(amount);
        fillFromScratch(HEADER_SIZE + encoder.encodedLength());
    }
}
