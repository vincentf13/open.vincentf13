package open.vincentf13.service.spot_exchange.infra;

/** 
  定點數運算工具類 (金融級安全版)
 */
public class DecimalUtil {
    public static final long SCALE = 100_000_000L; // 10^8

    /** 
      乘法並向下取整 (入帳用，保護系統)
     */
    public static long mulFloor(long a, long b) {
        return (java.math.BigInteger.valueOf(a)
                .multiply(java.math.BigInteger.valueOf(b))
                .divide(java.math.BigInteger.valueOf(SCALE))).longValue();
    }

    /** 
      乘法並向上取整 (扣款用)
     */
    public static long mulCeil(long a, long b) {
        java.math.BigInteger res = java.math.BigInteger.valueOf(a).multiply(java.math.BigInteger.valueOf(b));
        java.math.BigInteger scaleBI = java.math.BigInteger.valueOf(SCALE);
        java.math.BigInteger[] divRem = res.divideAndRemainder(scaleBI);
        if (divRem[1].signum() > 0) {
            return divRem[0].add(java.math.BigInteger.ONE).longValue();
        }
        return divRem[0].longValue();
    }
}
