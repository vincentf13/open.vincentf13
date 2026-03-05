package open.vincentf13.sdk.algo.greedy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
  CanPlaceFlowers 演算法單元測試
 */
class CanPlaceFlowersTest {

    @Test
    @DisplayName("測試所有種花案例")
    void testCanPlaceFlowers() {
        // 案例 1: 可以在空位種花
        assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{1, 0, 0, 0, 1}, 1));
        
        // 案例 2: 不能在相鄰格種花
        assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[]{1, 0, 0, 0, 1}, 2));
        
        // 案例 3: 邊界情況 (頭部種花)
        assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{0, 0, 1, 0, 1}, 1));
        
        // 案例 4: 邊界情況 (尾部種花)
        assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{1, 0, 1, 0, 0}, 1));
        
        // 案例 5: 只有一個位置且為空
        assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{0}, 1));
    }
}
