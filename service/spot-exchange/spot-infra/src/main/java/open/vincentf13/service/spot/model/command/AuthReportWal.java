package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.wire.Marshallable;
import net.openhft.chronicle.wire.WireIn;
import net.openhft.chronicle.wire.WireOut;

/**
 * 核心引擎認證結果回報 (WAL 載體)
 */
@Data
public class AuthReportWal implements Marshallable {
    private long matchingSeq;
    private long userId;

    @Override
    public void writeMarshallable(WireOut wire) {
        wire.write("matchingSeq").int64(matchingSeq);
        wire.write("userId").int64(userId);
    }

    @Override
    public void readMarshallable(WireIn wire) {
        matchingSeq = wire.read("matchingSeq").int64();
        userId = wire.read("userId").int64();
    }
}
