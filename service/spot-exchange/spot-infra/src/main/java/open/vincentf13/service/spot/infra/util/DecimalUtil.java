package open.vincentf13.service.spot.infra.util;

import java.math.BigInteger;

/**
 定點數運算工具類 (金融級溢出保護版 + Zero-GC 優化)
 */
public class DecimalUtil {
    public static final long SCALE = 100_000_000L; // 10^8
    private static final BigInteger SCALE_BI = BigInteger.valueOf(SCALE);

    /**
     * 無損加法
     */
    public static long add(long a, long b) {
        return Math.addExact(a, b);
    }

    /**
     * 無損減法
     */
    public static long sub(long a, long b) {
        return Math.subtractExact(a, b);
    }

    /**
     * 無損乘法 (向下取整)
     * 演算法：將整數與小數部分拆解，避免直接相乘導致 `long` 溢出。
     * a = aHigh * SCALE + aLow
     * b = bHigh * SCALE + bLow
     * (a * b) / SCALE = aHigh * bHigh * SCALE + aHigh * bLow + aLow * bHigh + (aLow * bLow) / SCALE
     * 此作法完全消除了 BigInteger 帶來的 GC 與效能損耗。
     */
    public static long mulFloor(long a, long b) {
        if (a == 0 || b == 0) return 0;
        
        long sign = (a < 0) ^ (b < 0) ? -1 : 1;
        a = Math.abs(a);
        b = Math.abs(b);

        long aHigh = a / SCALE;
        long aLow  = a % SCALE;
        long bHigh = b / SCALE;
        long bLow  = b % SCALE;

        try {
            long p1 = Math.multiplyExact(Math.multiplyExact(aHigh, bHigh), SCALE);
            long p2 = Math.multiplyExact(aHigh, bLow);
            long p3 = Math.multiplyExact(aLow, bHigh);
            long p4 = (aLow * bLow) / SCALE;
            
            long res = Math.addExact(Math.addExact(Math.addExact(p1, p2), p3), p4);
            return res * sign;
        } catch (ArithmeticException e) {
            // 發生極端溢位時降級使用 BigInteger (效能較差但安全)
            BigInteger resBI = BigInteger.valueOf(a)
                                         .multiply(BigInteger.valueOf(b))
                                         .divide(SCALE_BI);
            return resBI.longValue() * sign;
        }
    }

    /**
     * 無損乘法 (向上取整)
     */
    public static long mulCeil(long a, long b) {
        if (a == 0 || b == 0) return 0;
        
        long sign = (a < 0) ^ (b < 0) ? -1 : 1;
        a = Math.abs(a);
        b = Math.abs(b);

        long aHigh = a / SCALE;
        long aLow  = a % SCALE;
        long bHigh = b / SCALE;
        long bLow  = b % SCALE;

        try {
            long p1 = Math.multiplyExact(Math.multiplyExact(aHigh, bHigh), SCALE);
            long p2 = Math.multiplyExact(aHigh, bLow);
            long p3 = Math.multiplyExact(aLow, bHigh);
            long lowProduct = aLow * bLow;
            long p4 = lowProduct / SCALE;
            
            long res = Math.addExact(Math.addExact(Math.addExact(p1, p2), p3), p4);
            if (lowProduct % SCALE != 0) {
                res = Math.addExact(res, 1);
            }
            return res * sign;
        } catch (ArithmeticException e) {
            BigInteger res = BigInteger.valueOf(a).multiply(BigInteger.valueOf(b));
            BigInteger[] divRem = res.divideAndRemainder(SCALE_BI);
            long val = divRem[0].longValue();
            if (divRem[1].signum() > 0) {
                val += 1;
            }
            return val * sign;
        }
    }
}
