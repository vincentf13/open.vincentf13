package open.vincentf13.service.spot.model;

import lombok.Data;

/**
 * 可重用的訂單請求模型
 */
@Data
public class OrderRequest {
    private String cid;
    // 預初始化內部物件，配合 readerForUpdating 實現 Zero-Allocation 解析
    private Params params = new Params();

    @Data
    public static class Params {
        private long userId;
        private long symbolId;
        private long price;
        private long qty;
        private int side;
    }
    
    /** 
      重置關鍵欄位 (可選，readerForUpdating 會覆蓋舊值，但字串建議重置)
     */
    public void reset() {
        this.cid = null;
    }
}
