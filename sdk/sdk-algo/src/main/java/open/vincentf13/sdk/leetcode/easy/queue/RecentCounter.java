package open.vincentf13.sdk.leetcode.easy.queue;

import java.util.LinkedList;
import java.util.Queue;

/**
 LeetCode 933: Number of Recent Calls
 https://leetcode.com/problems/number-of-recent-calls/
 
 You have a RecentCounter class which counts the number of recent requests within a certain time frame.
 Implement the RecentCounter class:
 - RecentCounter() Initializes the counter with zero recent requests.
 - int ping(int t) Adds a new request at time t, where t represents some time in milliseconds,
 and returns the number of requests that has happened in the past 3000 milliseconds (including the new request).
 Specifically, return the number of requests that have happened in the inclusive range [t - 3000, t].
 It is guaranteed that every call to ping uses a strictly increasing value of t.
 */
public class RecentCounter {
    
    private final Queue<Integer> requests;
    
    public RecentCounter() {
        this.requests = new LinkedList<>();
    }
    
    /**
     在時間 t 添加新請求並返回過去 3000 毫秒內的請求總數。
     
     使用隊列 (Queue) 技巧：
     1. 將新請求時間 t 加入隊列。
     2. 檢查隊列頭部的請求時間，若其早於 t - 3000，則該請求已過期，將其從隊列中移除。
     3. 重複上述移除步驟，直到隊列頭部的請求在 [t - 3000, t] 範圍內。
     4. 隊列的大小即為所求。
     
     時間複雜度: 每次 ping 為攤還 O(1)。每個請求最多入隊一次、出隊一次。
     空間複雜度: O(W)，W 為滑動視窗的大小（本題中最多 3000ms 內的請求數，上限 3001 次調用）。
     
     @param t 請求時間（毫秒）
     @return [t - 3000, t] 範圍內的請求數
     */
    public int ping(int t) {
        requests.offer(t);
        while (!requests.isEmpty() && requests.peek() < t - 3000) {
            requests.poll();
        }
        return requests.size();
    }
}
