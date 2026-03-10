package open.vincentf13.sdk.algo.math.number;

import lombok.experimental.UtilityClass;

/**
 最大公因數 (Greatest Common Divisor, GCD)
 實作歐幾里得算法 (Euclidean Algorithm)
 對應 CLRS 第 31.2 節
 */
@UtilityClass
public class GreatestCommonDivisor {
    
    /**
     計算兩個整數的最大公因數
     使用遞歸實現：gcd(a, b) = gcd(b, a mod b)
     
     @param a 整數 a
     @param b 整數 b
     @return a 與 b 的最大公因數
     */
    public static int gcd(int a,
                          int b) {
        return b == 0 ? a : gcd(b, a % b);
    }
    
    /**
     計算兩個長整數的最大公因數
     
     @param a 長整數 a
     @param b 長整數 b
     @return a 與 b 的最大公因數
     */
    public static long gcd(long a,
                           long b) {
        return b == 0 ? a : gcd(b, a % b);
    }
}
