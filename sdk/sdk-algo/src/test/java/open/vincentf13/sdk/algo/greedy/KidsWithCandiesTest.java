package open.vincentf13.sdk.algo.greedy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
  KidsWithCandies 演算法單元測試
 */
class KidsWithCandiesTest {

    @Test
    @DisplayName("案例 1: 擁有糖果最多的孩子")
    void testKidsWithCandiesCase1() {
        int[] candies = {2, 3, 5, 1, 3};
        int extraCandies = 3;
        List<Boolean> result = KidsWithCandies.kidsWithCandies(candies, extraCandies);
        
        // 期望結果: [true, true, true, false, true]
        assertEquals(List.of(true, true, true, false, true), result);
    }

    @Test
    @DisplayName("案例 2: 所有孩子加糖果後都能成為最大值")
    void testKidsWithCandiesCase2() {
        int[] candies = {4, 2, 1, 1, 2};
        int extraCandies = 1;
        List<Boolean> result = KidsWithCandies.kidsWithCandies(candies, extraCandies);
        
        // 期望結果: [true, false, false, false, false]
        assertEquals(List.of(true, false, false, false, false), result);
    }

    @Test
    @DisplayName("案例 3: 單一小孩的情況")
    void testKidsWithCandiesCase3() {
        int[] candies = {12, 1, 12};
        int extraCandies = 10;
        List<Boolean> result = KidsWithCandies.kidsWithCandies(candies, extraCandies);
        
        // 期望結果: [true, false, true]
        assertEquals(List.of(true, false, true), result);
    }
}
