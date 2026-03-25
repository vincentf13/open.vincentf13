package open.vincentf13.service.spot.infra.chronicle;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;

/**
 * 實現 100% Zero-GC 的 Long 型別 Flyweight 鍵值。
 * 用於替換 ChronicleMap 中的 java.lang.Long 鍵，以消除 Autoboxing 導致的物件分配。
 */
@Data
@EqualsAndHashCode
public class LongValue implements BytesMarshallable {
    private long value;

    public LongValue() {
    }

    public LongValue(long value) {
        this.value = value;
    }

    public void set(long value) {
        this.value = value;
    }

    @Override
    public void readMarshallable(BytesIn bytes) {
        this.value = bytes.readLong();
    }

    @Override
    public void writeMarshallable(BytesOut bytes) {
        bytes.writeLong(this.value);
    }
}
