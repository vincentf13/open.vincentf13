package open.vincentf13.service.spot.ws.wal;

/** WAL 下單指令業務欄位 (pre-allocated per Disruptor slot) */
public class WalOrderCreate {
    public int symbolId;
    public long price;
    public long qty;
    public byte side;
    public long clientOrderId;
}
