package open.vincentf13.service.spot.infra.aeron;

import io.aeron.Publication;
import io.aeron.logbuffer.BufferClaim;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.IdleStrategy;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/** 
 Aeron 通訊工具類
 封裝了高效能、零拷貝的傳輸輔助方法
 */
public class AeronUtil {

    /** 
     實現「三階段提交」的極致零拷貝發送：
     1. 空間申請 (Claim)：在 Aeron 環形緩衝區中原子性地預留一段空間，此時該空間被當前執行緒「鎖定」專用。
     2. 原地寫入 (In-place Write)：透過傳入的 writer 直接在 Aeron 的堆外內存上組裝數據，消除數據拷貝開銷。
     3. 最終提交 (Commit)：更新訊息 Header，讓這段內存數據對網絡端的接收者正式可見。
     
     @param publication Aeron 發布通道
     @param bufferClaim 執行緒私有的 Claim 佔位對象
     @param length 欲申請的總字節長度
     @param idleStrategy 背壓時的重試/休眠策略
     @param running 運行狀態旗標，用於在中斷時安全退出
     @param writer 寫入回調，提供申請到的 Buffer (MutableDirectBuffer) 與其偏移量 (Offset)
     */
    public static void claimAndSend(
            Publication publication, 
            BufferClaim bufferClaim, 
            int length, 
            IdleStrategy idleStrategy, 
            AtomicBoolean running, 
            BiConsumer<MutableDirectBuffer, Integer> writer) {
        
        // tryClaim 會在 Aeron 緩衝區滿 (Back-pressure) 或狀態異常時返回負數
        // 我們採用循環重試策略，配合 IdleStrategy 減少 CPU 消耗，直到成功申請到空間
        while (publication.tryClaim(length, bufferClaim) < 0) {
            if (!running.get()) return;
            idleStrategy.idle();
        }
        
        try {
            // 直接在預留的內存區塊上執行寫入
            // buffer: Aeron 底層的環形緩衝區 (MutableDirectBuffer)
            // offset: 目前這條訊息在該緩衝區中的起始寫入位址
            writer.accept(bufferClaim.buffer(), bufferClaim.offset());
        } finally {
            // 提交發送 (Commit)
            bufferClaim.commit();
        }
    }
}
