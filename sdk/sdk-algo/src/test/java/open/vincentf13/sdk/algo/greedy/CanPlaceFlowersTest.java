package open.vincentf13.sdk.algo.greedy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CanPlaceFlowersTest {

  @Test
void testCanPlaceFlowers() {
    // --- 最小邊界測試 ---
    // 空陣列
    assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{}, 0));
    assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[]{}, 1));
    
    // 長度為 1
    assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{0}, 1));
    assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[]{1}, 1));

    // --- 邊界位置測試 (頭與尾) ---
    // 起點可以種
    assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{0, 0, 1}, 1));
    // 終點可以種
    assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{1, 0, 0}, 1));
    // 頭尾都不能種
    assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[]{0, 1, 0}, 1));

    // --- 連續空位與貪婪策略測試 ---
    // 中間剛好有三個 0
    assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{1, 0, 0, 0, 1}, 1));
    // 五個 0 最多種 3 朵 (索引 0, 2, 4)
    assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{0, 0, 0, 0, 0}, 3));
    // 五個 0 種 4 朵則失敗
    assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[]{0, 0, 0, 0, 0}, 4));

    // --- 飽和與衝突測試 ---
    // 已經種滿
    assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[]{1, 0, 1, 0, 1}, 1));
    // 只有兩個 0 在中間，無法種任何花
    assertFalse(CanPlaceFlowers.canPlaceFlowers(new int[]{1, 0, 0, 1}, 1));
    
    // --- 大量需求測試 ---
    // n = 0 應該永遠回傳 true
    assertTrue(CanPlaceFlowers.canPlaceFlowers(new int[]{1, 1, 1}, 0));
}
}
