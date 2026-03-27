package open.vincentf13.service.spot.infra.alloc;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

public class NativeUnsafeBuffer {
    private final ByteBuffer rawBuffer;
    private final MutableDirectBuffer unsafeBuffer;

    public NativeUnsafeBuffer(int capacity) {
        this.rawBuffer = ByteBuffer.allocateDirect(capacity);
        this.unsafeBuffer = new UnsafeBuffer(rawBuffer);
    }

    public long addressOffset() {
        return unsafeBuffer.addressOffset();
    }

    public int capacity() {
        return unsafeBuffer.capacity();
    }
}
