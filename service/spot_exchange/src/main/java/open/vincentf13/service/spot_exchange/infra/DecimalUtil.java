package open.vincentf13.service.spot_exchange.infra;

/** 
  定點數運算工具類
 */
public class DecimalUtil {
    public static final long SCALE = 100_000_000L; // 10^8

    public static long fromDouble(double val) {
        return (long) (val * SCALE);
    }

    public static double toDouble(long val) {
        return (double) val / SCALE;
    }

    public static long multiply(long a, long b) {
        return (a * b) / SCALE;
    }
}
