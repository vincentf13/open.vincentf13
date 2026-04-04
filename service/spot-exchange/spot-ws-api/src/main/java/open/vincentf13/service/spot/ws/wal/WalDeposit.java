package open.vincentf13.service.spot.ws.wal;

/** WAL 充值指令業務欄位 (pre-allocated per Disruptor slot) */
public class WalDeposit {
    public int assetId;
    public long amount;
}
