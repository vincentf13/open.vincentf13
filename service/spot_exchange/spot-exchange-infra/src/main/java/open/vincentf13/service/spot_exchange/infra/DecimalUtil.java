package open.vincentf13.service.spot_exchange.infra;

import java.math.BigInteger;

/** 
  定點數運算工具類 (金融級溢出保護版 + Zero-GC 優化)
 */
public class DecimalUtil {
    public static final long SCALE = 100_000_000L; // 10^8
    private static final BigInteger SCALE_BI = BigInteger.valueOf(SCALE);

    /** 
      大額安全乘法並向下取整 (入帳用)
     */
    public static long mulFloor(long a, long b) {
        if (a == 0 || b == 0) return 0;
        
        long aInt = a / SCALE;
        long aFrac = a % SCALE;
        
        // Zero-GC Fast Path: 對絕大多數不超過 900 萬的乘數進行純 long 運算
        if (aFrac == 0 || b <= 90_000_000_000L) {
            return aInt * b + (aFrac * b) / SCALE;
        }

        // 巨鯨交易回退至 BigInteger 防溢出
        return BigInteger.valueOf(a)
                .multiply(BigInteger.valueOf(b))
                .divide(SCALE_BI)
                .longValue();
    }

    /** 
      大額安全乘法並向上取整 (扣款用)
     */
    public static long mulCeil(long a, long b) {
        if (a == 0 || b == 0) return 0;
        
        long aInt = a / SCALE;
        long aFrac = a % SCALE;
        
        if (aFrac == 0 || b <= 90_000_000_000L) {
            long fracProd = aFrac * b;
            long res = aInt * b + fracProd / SCALE;
            if (fracProd % SCALE > 0) res++;
            return res;
        }

        BigInteger res = BigInteger.valueOf(a).multiply(BigInteger.valueOf(b));
        BigInteger[] divRem = res.divideAndRemainder(SCALE_BI);
        if (divRem[1].signum() > 0) {
            return divRem[0].add(BigInteger.ONE).longValue();
        }
        return divRem[0].longValue();
    }
}
