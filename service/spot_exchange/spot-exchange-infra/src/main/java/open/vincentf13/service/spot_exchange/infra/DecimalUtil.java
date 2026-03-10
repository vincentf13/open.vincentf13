package open.vincentf13.service.spot_exchange.infra;

import java.math.BigInteger;

/** 
  定點數運算工具類 (金融級溢出保護版)
 */
public class DecimalUtil {
    public static final long SCALE = 100_000_000L; // 10^8
    private static final BigInteger SCALE_BI = BigInteger.valueOf(SCALE);

    /** 
      大額安全乘法並向下取整 (入帳用)
      支援高達 128 位的中間乘積，防止 price * qty 溢出
     */
    public static long mulFloor(long a, long b) {
        if (a == 0 || b == 0) return 0;
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
        BigInteger res = BigInteger.valueOf(a).multiply(BigInteger.valueOf(b));
        BigInteger[] divRem = res.divideAndRemainder(SCALE_BI);
        if (divRem[1].signum() > 0) {
            return divRem[0].add(BigInteger.ONE).longValue();
        }
        return divRem[0].longValue();
    }
}
