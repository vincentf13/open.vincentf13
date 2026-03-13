package open.vincentf13.service.spot.infra.aeron;

import org.agrona.MutableDirectBuffer;

/**
 Aeron 基礎工具
 */
public class AeronUtil {

    @FunctionalInterface
    public interface AeronHandler {
        void onFill(MutableDirectBuffer buffer, int offset);
    }
}
