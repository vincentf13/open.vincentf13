# CPU 佈局 (Intel Core Ultra 9 285H)

## 硬體拓撲（coreinfo 實測驗證）

| OS 邏輯核 | 類型 | 架構 | L1D | L2 | L3 |
|---|---|---|---|---|---|
| **0** | P-core | Lion Cove | 48KB/12w | **3 MB 私有** | ✓ 24MB 共享 |
| **1** | P-core | Lion Cove | 48KB/12w | 3 MB 私有 | ✓ |
| **2** | E-core | Skymont | 32KB/8w | **4 MB 共享** `{2,3,4,5}` | ✓ |
| **3** | E-core | Skymont | 32KB/8w | 4 MB 共享 | ✓ |
| **4** | E-core | Skymont | 32KB/8w | 4 MB 共享 | ✓ |
| **5** | E-core | Skymont | 32KB/8w | 4 MB 共享 | ✓ |
| **6** | E-core | Skymont | 32KB/8w | **4 MB 共享** `{6,7,8,9}` | ✓ |
| **7** | E-core | Skymont | 32KB/8w | 4 MB 共享 | ✓ |
| **8** | E-core | Skymont | 32KB/8w | 4 MB 共享 | ✓ |
| **9** | E-core | Skymont | 32KB/8w | 4 MB 共享 | ✓ |
| **10** | P-core | Lion Cove | 48KB/12w | 3 MB 私有 | ✓ |
| **11** | P-core | Lion Cove | 48KB/12w | 3 MB 私有 | ✓ |
| **12** | P-core | Lion Cove | 48KB/12w | 3 MB 私有 | ✓ |
| **13** | P-core | Lion Cove | 48KB/12w | 3 MB 私有 | ✓ |
| **14** | LP E-core | Skymont LP (SoC tile) | 32KB/8w | **2 MB 共享** `{14,15}` | **✗ 不在 L3** |
| **15** | LP E-core | Skymont LP (SoC tile) | 32KB/8w | 2 MB 共享 | **✗** |

### 重點
- **P-core 不是連號**：0, 1, 10, 11, 12, 13（共 6 個）
- **E-core 分 2 個 cluster**：`{2,3,4,5}`, `{6,7,8,9}` 各有獨立 4MB L2
- **LP E-core 在 SoC tile**，不共享 compute tile 的 24MB L3（跨 tile 讀取 L3 延遲較高）
- 沒有 Hyper-Threading，16 個邏輯核 = 16 個實體核

### 特別注意：P0 (core 0)
Windows 預設將 **ISR / DPC 主要路由到 core 0**。即使執行緒是 `RealTime` 優先級，ISR 和 DPC 仍在 interrupt level 運行，**會短暫搶走 P0**。實測結果：
- matching worker 在 P0：transport p99 = **243.7 μs**
- matching worker 在 P13：transport p99 = **14.18 μs** (-94%)

**結論：任何對延遲敏感的 busy-spin 執行緒都不應放在 P0**。本系統將 P0 完全排除在 Java process mask 之外，留給 OS。

---

## 當前分配（壓測腳本 `gemini_stress_test_final.ps1`）

### 執行緒對應核表

| Core | 類型 | 角色 | Pin 方式 | CPU 使用 |
|---|---|---|---|---|
| **P0** | P | **(空)** 留給 OS DPC/ISR | — | OS only |
| **P1** | P | gateway gateway-sender (Worker, busy-spin) | `spot.affinity.cores` | 100% busy-spin |
| **E2** | E cluster1 | gateway 背景 / GC / JIT | 進程 mask 內漂移 | 低 |
| **E3** | E cluster1 | gateway 背景 / GC / JIT | 同上 | 低 |
| **E4** | E cluster1 | driver Aeron sender (Backoff idle) | `aeron.sender.affinity=4` | ~0%（IPC 無 UDP 工作） |
| **E5** | E cluster1 | driver Aeron receiver (Backoff idle) | `aeron.receiver.affinity=5` | ~0% |
| **E6** | E cluster2 | **benchmark 獨享** | benchmark process mask | busy-spin (benchmark main + NIO) |
| **E7** | E cluster2 | benchmark 獨享 | 同上 | busy-spin |
| **E8** | E cluster2 | benchmark 獨享 | 同上 | busy-spin |
| **E9** | E cluster2 | benchmark 獨享 | 同上 | busy-spin |
| **P10** | P | gateway report-receiver (Worker, busy-spin) | `spot.affinity.cores` | 100% busy-spin |
| **P11** | P | driver Aeron conductor (BusySpin idle) | `aeron.conductor.affinity=11` | 100% busy-spin |
| **P12** | P | gateway netty worker (Worker pool, pinned) | `spot.affinity.cores=1,10,12` | event-loop |
| **P13** | P | matching MatchingReceiver (Worker, busy-spin) | `spot.affinity.cores=13` (matching process) | 100% busy-spin |
| **LP14** | LP | 所有 Java process 共享 GC / 背景 | 多個 process mask 交集 | 低，偶發 GC |
| **LP15** | LP | 所有 Java process 共享 GC / 背景 | 同上 | 低，偶發 GC |

### 各 JVM process affinity mask

| Process | Mask | 包含的 cores | 意圖 |
|---|---|---|---|
| **driver (MediaDriver)** | `51248` | `{4, 5, 11, 14, 15}` | P11 conductor (busy-spin) + E4 sender + E5 receiver + LP 背景 |
| **matching** | `57404` | `{2, 3, 4, 5, 13, 14, 15}` | P13 worker + E-cluster1 GC + LP GC |
| **gateway (ws-api)** | `54334` | `{1, 2, 3, 4, 5, 10, 12, 14, 15}` | P1/P10/P12 熱路徑 + E-cluster1 背景 + LP GC（排除 P0） |
| **benchmark** | `960` | `{6, 7, 8, 9}` | E-cluster2 獨享 4MB L2 |

### 關鍵 JVM / Aeron 參數

**driver (`gemini_stress_test_final.ps1:199-214`)**
```
-Daeron.threading.mode=DEDICATED
-Daeron.conductor.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy
-Daeron.sender.idle.strategy=org.agrona.concurrent.BackoffIdleStrategy
-Daeron.receiver.idle.strategy=org.agrona.concurrent.BackoffIdleStrategy
-Daeron.conductor.affinity=11
-Daeron.sender.affinity=4
-Daeron.receiver.affinity=5
-Daeron.term.buffer.length=64m
-Daeron.ipc.term.buffer.length=256m
```

**gateway (`ws-api-throughput.args` + `gemini_stress_test_final.ps1`)**
```
-Dspot.affinity.cores=1,10,12
-Dspot.wal.bypass=true
-XX:+UseZGC -Xms4G -Xmx4G -XX:MaxDirectMemorySize=4G
-XX:GuaranteedSafepointInterval=0
-XX:CICompilerCount=8
-Dnetty.worker.count=2
```

**matching (`matching-low-latency.args` + `gemini_stress_test_final.ps1`)**
```
-Dspot.affinity.cores=13
-XX:+UseZGC -Xms4G -Xmx4G -XX:MaxDirectMemorySize=8G
-XX:GuaranteedSafepointInterval=0
-XX:CICompilerCount=4
-XX:InlineSmallCode=4000
```

---

## 重要設計原則

### 1. 熱 busy-spin 執行緒只上 P-core
6 個 P-core 分配給：
1. `matching MatchingReceiver`（P13）— 撮合熱路徑
2. `gateway gateway-sender`（P1）— Netty → Aeron 熱路徑
3. `gateway report-receiver`（P10）— Aeron → Netty 回路徑
4. `driver Aeron conductor`（P11）— Aeron 位點計數器
5. `gateway netty worker`（P12）— 部分 netty worker pinned
6. **P0 完全不用**（OS DPC 核心，會吃延遲）

### 2. 冷路徑執行緒避開 P-core
- Aeron driver sender/receiver 改用 `BackoffIdleStrategy`（IPC-only 下本質 idle），放 E4/E5，不燒 P-core
- Netty 另一個 worker 在 pool 用盡後會 fall back 到 E-cluster1 漂移（benchmark 只有 1 條連線，第二個 netty worker idle，可接受）

### 3. L2 cluster 隔離
- **E-cluster1 `{2,3,4,5}`**：gateway/matching/driver 的 GC/JIT/背景共用 4MB L2
- **E-cluster2 `{6,7,8,9}`**：benchmark 獨享 4MB L2，避免與 server 側 cache coherency 衝突
- **LP `{14,15}`**：所有 Java process 的低優先背景工作共用 2MB L2（跨 SoC tile，延遲較高但不影響熱路徑）

### 4. 驗證工具
- **[Sysinternals coreinfo](https://learn.microsoft.com/en-us/sysinternals/downloads/coreinfo)** `-l` flag：驗證 OS 邏輯核 ↔ 實體架構對應。**不要信任 script 註解，一定要實測**。
- **`TestVerificationController.CPU_METRICS`** 的 `current_cpu_id` + `cpu_affinity_history`：壓測中驗證每個 Worker 實際落在哪個 core。

---

## 歷史備忘：曾經踩過的坑

### 坑 1：Script 原本假設 P-core = `{0-5}`
**症狀**：壓測延遲尾巴一直壓不下去，`transport p99` 穩定 225μs。

**原因**：Ultra 9 285H 的 P-core 分佈是 `{0, 1, 10, 11, 12, 13}`，不是 `{0-5}`。script 註解寫「P-core 0-5」是從 Meteor Lake / Alder Lake 的假設照抄來的，**Arrow Lake-H 的 P/E/LP-E 分佈不同**。實測前 gateway 的 report-receiver、netty workers、driver conductor、Aeron sender/receiver 全部跑在 E-core 上。

**修復**：用 `coreinfo -l` 實測後重新分配。詳見 commit `c1b949ea`。

### 坑 2：Matching worker 在 P0 吃 Windows DPC
**症狀**：伺服器內部 `transport p99 = 243μs`，但 `gateway_total p99 = 3μs`、`matching p99 = 2μs`、`report_delivery p99 = 4μs`。**240μs 不知去向**。

**原因**：transport 計時是 `Netty 收到` → `matching engine 收到`。中間的「Aeron IPC 傳輸 + matching MatchingReceiver poll dispatch」本應 <1μs，但 matching worker 在 P0 上，被 Windows ISR/DPC 週期性搶走 ~240μs，期間 gateway 累積的訊息在 term buffer 排隊，最後一筆顯示 p99=240μs。

**修復**：matching worker 從 P0 搬到 P13，transport p99 從 243μs → **14μs**（-94%）。詳見 commit `e948acd0`。

### 坑 3：Netty workers 沒透過 AffinityUtil pinned
**症狀**：就算 matching 離開 P0，benchmark RTT 改善有限。

**原因**：gateway `spot.affinity.cores=1,10` 只給 2 個 core，被 gateway-sender 和 report-receiver 鎖光。netty workers 呼叫 `AffinityUtil.acquireAndBind()` 時 pool 已空，**fall back 到「自由調度」** → 在 gateway process mask 內漂移，容易落到 P0（還在 mask 內）或 E-core。

**修復**：
1. gateway process mask 排除 P0
2. 擴大 `spot.affinity.cores=1,10,12`，讓第一個 netty worker 可以 pin 到 P12

### 坑 4：Aeron sender/receiver 在 IPC-only 下用 BusySpinIdleStrategy 浪費 CPU
**症狀**：driver sender/receiver 的 E-core 整秒 100% CPU，實際在做空迴圈。

**原因**：DEDICATED threading 模式下 sender/receiver agent 預設 BusySpin。IPC-only 場景（沒 UDP 流量）sender/receiver 沒實際工作可做，空燒。

**修復**：改用 `BackoffIdleStrategy`，空閒時 park，有工作時響應。E-core CPU 使用率降到 ~0%。

---

## 當前量測結果（60K/sec, bypass mode, diagnose）

### 伺服器內部 p50 / p90 / p99（diagnose metrics）

| 階段 | p50 | p90 | p99 |
|---|---|---|---|
| netty_process (Netty 收 → disruptor publish) | 100 ns | 200 ns | **376 ns** |
| disruptor_wait (disruptor → sender 撈) | 200 ns | 300 ns | **676 ns** |
| sender_encode (sender → Aeron send 完成) | 200 ns | 300 ns | **496 ns** |
| control_poll (控制通道 poll) | 0 | 100 ns | 100 ns |
| **gateway_total** (Netty 收 → Aeron send 完成) | 500 ns | 612 ns | **1 μs** |
| **transport** (Netty 收 → matching engine 收) | 828 ns | 3.56 μs | **13.4 μs** |
| matching (engine 處理) | 1.08 μs | 1.49 μs | 2.12 μs |
| report_delivery (matching → gateway 收) | 500 ns | 2.45 μs | 3.48 μs |

**server 內部 p99 合計 ≈ 19 μs**

### Benchmark RTT (client 觀察)

| 百分位 | 延遲 |
|---|---|
| p50 | 0.23 ms |
| p90 | 0.89 ms |
| p99 | **1.28 ms** |

Server 內部 ~19μs vs client RTT 1.28ms，**~1.26ms 落在 client side + WS 回程**。這是 benchmark 在 E-core 上跑 busy-spin + Netty event-loop 的必然代價，與 server 設計無關。如需更低的「對外 SLA」延遲，需要從外部機器跑 benchmark 而不是 loopback。
