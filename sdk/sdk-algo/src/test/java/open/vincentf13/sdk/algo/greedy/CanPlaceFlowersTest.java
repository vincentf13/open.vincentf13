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
    @DisplayName("案例 1: 可以在空位種花")
    void testCanPlaceFlowersCase1() {
        int[] flowerbed = {1, 0, 0, 0, 1};
        int n = 1;
        assertTrue(CanPlaceFlowers.canPlaceFlowers(flowerbed, n));
    }

    @Test
    @DisplayName("案例 2: 不能在相鄰格種花")
    void testCanPlaceFlowersCase2() {
        int[] flowerbed = {1, 0, 0, 0, 1};
        int n = 2;
        assertFalse(CanPlaceFlowers.canPlaceFlowers(flowerbed, n));
    }

    @Test
    @DisplayName("案例 3: 邊界情況 (頭部種花)")
    void testCanPlaceFlowersCase3() {
        int[] flowerbed = {0, 0, 1, 0, 1};
        int n = 1;
        assertTrue(CanPlaceFlowers.canPlaceFlowers(flowerbed, n));
    }

    @Test
    @DisplayName("案例 4: 邊界情況 (尾部種花)")
    void testCanPlaceFlowersCase4() {
        int[] flowerbed = {1, 0, 1, 0, 0};
        int n = 1;
        assertTrue(CanPlaceFlowers.canPlaceFlowers(flowerbed, n));
    }

    @Test
    @DisplayName("案例 5: 只有一個位置且為空")
    void testCanPlaceFlowersCase5() {
        int[] flowerbed = {0};
        int n = 1;
        assertTrue(CanPlaceFlowers.canPlaceFlowers(flowerbed, n));
    }
}
