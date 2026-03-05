package open.vincentf13.sdk.algo.array;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
  KidsWithCandies 演算法單元測試
 */
class KidsWithCandiesTest {

    @Test
    @DisplayName("測試所有小孩糖果案例")
    void testKidsWithCandies() {
        // 案例 1: 擁有糖果最多的孩子
        assertEquals(List.of(true, true, true, false, true), 
                KidsWithCandies.kidsWithCandies(new int[]{2, 3, 5, 1, 3}, 3));
        
        // 案例 2: 所有孩子加糖果後都能成為最大值
        assertEquals(List.of(true, false, false, false, false), 
                KidsWithCandies.kidsWithCandies(new int[]{4, 2, 1, 1, 2}, 1));
        
        // 案例 3: 單一小孩的情況
        assertEquals(List.of(true, false, true), 
                KidsWithCandies.kidsWithCandies(new int[]{12, 1, 12}, 10));
    }
}
