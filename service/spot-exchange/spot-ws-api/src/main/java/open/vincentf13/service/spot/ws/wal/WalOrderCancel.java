package open.vincentf13.service.spot.ws.wal;

/** WAL 撤單指令業務欄位 (pre-allocated per Disruptor slot) */
public class WalOrderCancel {
    public long orderId;
}
