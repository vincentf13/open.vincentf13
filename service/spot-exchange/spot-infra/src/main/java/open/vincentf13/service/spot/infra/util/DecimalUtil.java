package open.vincentf13.service.spot.infra.util;

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
        
        try {
            long aInt = a / SCALE;
            long aFrac = a % SCALE;
            
            // Fast Path: 利用 Math.multiplyExact 自動檢測溢出
            long res1 = Math.multiplyExact(aInt, b);
            long prod2 = Math.multiplyExact(aFrac, b);
            
            return Math.addExact(res1, prod2 / SCALE);
        } catch (ArithmeticException e) {
            // 巨鯨交易回退至 BigInteger 防溢出
            return BigInteger.valueOf(a)
                    .multiply(BigInteger.valueOf(b))
                    .divide(SCALE_BI)
                    .longValue();
        }
    }

    /** 
      大額安全乘法並向上取整 (扣款用)
     */
    public static long mulCeil(long a, long b) {
        if (a == 0 || b == 0) return 0;
        
        try {
            long aInt = a / SCALE;
            long aFrac = a % SCALE;
            
            long res1 = Math.multiplyExact(aInt, b);
            long prod2 = Math.multiplyExact(aFrac, b);
            long res2 = prod2 / SCALE;
            if (prod2 % SCALE > 0) res2++;
            
            return Math.addExact(res1, res2);
        } catch (ArithmeticException e) {
            BigInteger res = BigInteger.valueOf(a).multiply(BigInteger.valueOf(b));
            BigInteger[] divRem = res.divideAndRemainder(SCALE_BI);
            if (divRem[1].signum() > 0) {
                return divRem[0].add(BigInteger.ONE).longValue();
            }
            return divRem[0].longValue();
        }
    }
}
