package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;

/**
 * 核心引擎認證結果回報 (WAL 載體)
 */
@Data
public class AuthReportWal implements BytesMarshallable {
    private long matchingSeq;
    private long userId;

    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        bytes.writeLong(matchingSeq);
        bytes.writeLong(userId);
    }

    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        matchingSeq = bytes.readLong();
        userId = bytes.readLong();
    }
}
