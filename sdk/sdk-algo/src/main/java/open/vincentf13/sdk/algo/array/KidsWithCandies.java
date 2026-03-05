package open.vincentf13.sdk.algo.array;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * LeetCode 1431: Kids With the Greatest Number of Candies
 * https://leetcode.com/problems/kids-with-the-greatest-number-of-candies/
 *
 * <p>There are n kids with candies. You are given an integer array candies, where each candies[i]
 * represents the number of candies the ith kid has, and an integer extraCandies, denoting the
 * number of extra candies that you have.
 *
 * <p>Return a boolean array result of length n, where result[i] is true if, after giving the ith
 * kid all the extraCandies, they will have the greatest number of candies among all the kids, or
 * false otherwise.
 *
 * <p>Note that multiple kids can have the greatest number of candies.
 */
@UtilityClass
public class KidsWithCandies {

  /**
   * 判斷每個小孩在獲得額外糖果後是否能成為擁有最多糖果的人
   *
   * @param candies 每個小孩現有的糖果數量
   * @param extraCandies 額外的糖果數量
   * @return 布林值列表
   */
  public static List<Boolean> kidsWithCandies(int[] candies, int extraCandies) {
    List<Boolean> kidsWithCandies = new ArrayList<Boolean>(candies.length);

    // 暴力法:
    // 找到最大的數字
    // 數組都加上 extraCandies 後，超過最大數字的在返回值標上 true
    // O(2n)
    int max = 0;
    for (int candy : candies) {
      if (candy > max) max = candy;
    }

    for (int candie : candies) {
      if (candie + extraCandies >= max) {
        kidsWithCandies.add(true);
      } else {
        kidsWithCandies.add(false);
      }
    }
    return kidsWithCandies;
  }
}
