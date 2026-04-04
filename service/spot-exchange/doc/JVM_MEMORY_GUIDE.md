# JVM 記憶體區塊與監控指標指南

## 全局記憶體佈局

```
┌─────────────────────────────────────────────────────────────────┐
│                     OS 進程虛擬地址空間                           │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    Java Heap                              │   │
│  │  JVM 管理，GC 回收。所有 new 出來的物件都在這裡。           │   │
│  │  -Xms (初始大小) / -Xmx (最大大小)                        │   │
│  │                                                           │   │
│  │  ZGC: 不分 Eden/Survivor/Old，單一連續區域               │   │
│  │  G1:  分為 Eden / Survivor / Old / Humongous              │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                  Non-Heap (JVM 內部)                       │   │
│  │                                                           │   │
│  │  ┌─────────────┐  ┌──────────────┐  ┌────────────────┐  │   │
│  │  │  Metaspace   │  │  Code Cache   │  │ Compressed     │  │   │
│  │  │  類元數據     │  │  JIT 編譯碼   │  │ Class Space    │  │   │
│  │  └─────────────┘  └──────────────┘  └────────────────┘  │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                 堆外記憶體 (Off-Heap)                      │   │
│  │                                                           │   │
│  │  ┌──────────────────┐  ┌─────────────────────────────┐   │   │
│  │  │  Direct Memory    │  │  Memory-Mapped Files (mmap) │   │   │
│  │  │  ByteBuffer.      │  │  MappedByteBuffer           │   │   │
│  │  │  allocateDirect() │  │  FileChannel.map()          │   │   │
│  │  │                   │  │                              │   │   │
│  │  │  使用者：          │  │  使用者：                    │   │   │
│  │  │  • Netty ByteBuf  │  │  • Chronicle Map (狀態快照)  │   │   │
│  │  │  • Aeron Term Buf │  │  • Chronicle Queue (WAL)     │   │   │
│  │  │  • Unsafe.alloc   │  │  • Aeron Log Buffer         │   │   │
│  │  └──────────────────┘  └─────────────────────────────┘   │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Thread Stacks (每條線程 ~1MB)                             │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 各區塊詳解

### 1. Heap (堆記憶體)

**用途：** 所有 `new` 出來的 Java 物件。GC 的管轄範圍。

**JVM 參數：**
- `-Xms`：初始大小（committed 起點）
- `-Xmx`：最大大小（上限）
- `-XX:+AlwaysPreTouch`：啟動時將 committed 撐到 max，強制分配物理頁面

**三個指標：**

| 指標 | 意義 | 異常判斷 |
|------|------|----------|
| **used** | GC 後存活物件實際佔用 | 持續上漲 → 記憶體洩漏 |
| **committed** | JVM 已向 OS 申請的空間 | 應 = max（如果開了 AlwaysPreTouch） |
| **max** | `-Xmx` 設定的上限 | used 接近 max → OOM 風險 |

**解讀公式：**
```
使用率 = used / max × 100%
碎片率 = (committed - used) / committed × 100%

健康狀態：
  used / max < 70%  → 正常
  used / max > 85%  → GC 壓力大，考慮加大 Xmx 或排查洩漏
  committed < max   → AlwaysPreTouch 沒生效（啟動可能有 Page Fault）
```

**HFT 特別注意：**
- Zero-GC 設計下，Heap used 應該只在啟動時上漲（池化物件分配），運行時穩定不動
- 如果壓測中 used 持續緩慢上漲 → 有物件逃逸到 Heap（可能是隱式裝箱、字串拼接等）

---

### 2. Direct Memory (直接記憶體)

**用途：** `ByteBuffer.allocateDirect()` 分配的堆外記憶體。不經過 GC，由 `Cleaner` 機制回收。

**JVM 參數：**
- `-XX:MaxDirectMemorySize`：上限（預設 = `-Xmx`）

**關鍵使用者：**
- **Netty** `PooledByteBufAllocator`：WebSocket 收發包的池化 Buffer
- **Aeron** Term Buffer：IPC 通訊的共享記憶體
- **Disruptor** RingBuffer 的 WalEvent（如果使用 DirectByteBuffer）

**指標：**

| 指標 | 意義 | 異常判斷 |
|------|------|----------|
| **used** | 當前已分配的 Direct 記憶體 | 持續上漲 → ByteBuf 洩漏（忘記 release） |
| **max** | `-XX:MaxDirectMemorySize` | used 接近 max → OOM: Direct buffer memory |
| **count** | ByteBuffer 實例數量 | 持續增加 → 分配沒被回收 |

**解讀：**
```
健康狀態：
  used 穩定不動 → Netty 池化正常，Buffer 正確回收
  used 緩慢漲 → ByteBuf.retain() 後忘記 release()
  used 突然暴漲 → 大量新連線或大封包
```

---

### 3. Mapped Memory (記憶體映射)

**用途：** `FileChannel.map()` 建立的 mmap 映射。檔案內容直接映射到進程地址空間，讀寫如同操作記憶體。

**沒有 JVM 參數限制** — 受限於 OS 虛擬地址空間（64-bit 系統幾乎無限）。

**關鍵使用者：**
- **Chronicle Map**：所有狀態快照（orders、trades、balances 等 12 個 Map）
- **Chronicle Queue**：WAL 日誌（128MB blockSize × 滾動文件數）
- **Aeron**：Log Buffer 的底層 mmap 檔案

**指標：**

| 指標 | 意義 | 異常判斷 |
|------|------|----------|
| **used** | 所有 mmap 映射的總大小 | 應穩定 = Chronicle Map 文件總大小 + Queue blocks |
| **count** | MappedByteBuffer 實例數 | 增加 → 新的 Queue roll file 或 Map 擴容 |

**解讀：**
```
健康狀態：
  used 穩定（如 4925MB）→ 正常，Chronicle 文件大小固定
  used 突然增加 → Queue 滾動產生新文件，或 Map maxBloatFactor 觸發擴容
  
注意：mmap used ≠ 物理 RAM 佔用
  OS 會 lazy-load 頁面，only accessed pages 佔物理 RAM
  Pre-touch 確保所有頁面已載入
  OS 可能在記憶體壓力下 evict mmap 頁面（除非 mlock）
```

**與 Heap 的關鍵區別：**
- Heap 物件被 GC 管理，有 Stop-The-World 暫停風險
- Mapped 物件由 OS Page Cache 管理，不觸發 GC，但受 OS swap 影響
- HFT 選擇 mmap 而非 Heap 就是為了避免 GC 抖動

---

### 4. Metaspace (元空間)

**用途：** 儲存類的元數據（Class 定義、Method 結構、常量池）。Java 8 之前叫 PermGen。

**JVM 參數：**
- `-XX:MetaspaceSize`：初始閾值（超過觸發 GC）
- `-XX:MaxMetaspaceSize`：上限（預設無限）

**指標：**

| 指標 | 意義 | 異常判斷 |
|------|------|----------|
| **used** | 已載入的類元數據佔用 | 啟動後應穩定不動 |
| **committed** | JVM 已向 OS 申請的 Metaspace 空間 | 略大於 used（OS 頁面對齊） |

**解讀：**
```
健康狀態：
  啟動後穩定（如 65MB）→ 正常
  持續緩慢增長 → 類加載洩漏（通常是動態代理、反射、Groovy 腳本等）
  HFT 系統不應有動態類加載，Metaspace 必須穩定
```

---

### 5. Code Cache (JIT 編譯碼快取)

**用途：** 儲存 JIT 編譯器（C1/C2）產生的原生機器碼。

**JVM 參數：**
- `-XX:ReservedCodeCacheSize`：最大大小（預設 240MB）

**指標（參考用，通常不需要監控）：**

| 指標 | 意義 |
|------|------|
| **used** | 已編譯方法的機器碼佔用 |
| **max** | ReservedCodeCacheSize |

**為什麼通常不需要看：**
- JIT 編譯在 warmup 期完成，之後穩定
- 只有極端情況（超大應用 + 深度 inline）會撐滿 Code Cache
- 撐滿時 JVM 停止編譯新方法，退化為解釋執行（性能斷崖）

---

### 6. Thread Stacks (線程棧)

**用途：** 每條線程的呼叫棧（局部變量、方法幀）。

**JVM 參數：**
- `-Xss`：每條線程的棧大小（預設 512KB ~ 1MB）

**指標：**

| 指標 | 意義 | 異常判斷 |
|------|------|----------|
| **thread count** | 活躍線程數 | 持續增加 → 線程洩漏 |

**總棧空間 = thread_count × Xss**

**解讀：**
```
HFT 系統線程數應固定：
  Matching: ~10-15 (receiver + engine + Spring + GC + JIT)
  Gateway:  ~15-20 (boss + 4 workers + wal-writer + sender + Spring + Tomcat)
  
如果 thread count 隨時間增加 → 檢查是否有未關閉的 executor
```

---

## 監控指標完整表

| 區塊 | 指標 | Java API | 單位 |
|------|------|----------|------|
| Heap | used | `Runtime.totalMemory() - freeMemory()` | bytes |
| Heap | committed | `MemoryMXBean.getHeapMemoryUsage().getCommitted()` | bytes |
| Heap | max | `Runtime.maxMemory()` | bytes |
| Direct | used | `BufferPoolMXBean("direct").getMemoryUsed()` | bytes |
| Direct | max | `-XX:MaxDirectMemorySize` | bytes |
| Direct | count | `BufferPoolMXBean("direct").getCount()` | count |
| Mapped | used | `BufferPoolMXBean("mapped").getMemoryUsed()` | bytes |
| Mapped | count | `BufferPoolMXBean("mapped").getCount()` | count |
| Metaspace | used | `MemoryPoolMXBean("Metaspace").getUsage().getUsed()` | bytes |
| Metaspace | committed | `MemoryPoolMXBean("Metaspace").getUsage().getCommitted()` | bytes |
| Threads | count | `ThreadMXBean.getThreadCount()` | count |

---

## 快速診斷表

| 症狀 | 可能原因 | 查看哪個指標 |
|------|----------|-------------|
| GC 頻繁 / STW 增加 | Heap used 接近 max | Heap used/max |
| OOM: Java heap space | Heap 不夠或洩漏 | Heap used 趨勢 |
| OOM: Direct buffer memory | ByteBuf 洩漏 | Direct used/max |
| OOM: Metaspace | 動態類加載過多 | Metaspace used 趨勢 |
| OOM: unable to create thread | 線程太多 or 棧太大 | Thread count × Xss |
| 延遲抖動 (Jitter) | mmap Page Fault | Mapped used + Pre-touch 是否生效 |
| 延遲抖動 (Jitter) | GC 暫停 | GC pause history |
| 首筆訂單延遲高 | JIT 未預熱 | Code Cache used 是否穩定 |
| 性能退化 | Code Cache 滿 | Code Cache used/max |

---

## HFT 系統的理想記憶體特徵

```
啟動後 30 秒的穩態特徵：

Heap:
  used:      穩定（池化物件 + Spring 上下文 + 常駐快取）
  committed: = max（AlwaysPreTouch）

Direct:
  used:      穩定（Netty pool + Aeron buffer 固定大小）
  count:     穩定

Mapped:
  used:      穩定（Chronicle 文件大小固定，Pre-touched）
  count:     穩定（Queue roll 時 +1）

Metaspace:
  used:      穩定（所有類在啟動時載入完畢）

Threads:
  count:     穩定（所有 Worker 在啟動時創建完畢）

GC:
  pause:     ZGC 0ms (sub-millisecond)
  count:     極少（理想為 0，Warmup 觸發除外）
```

**核心原則：HFT 系統在穩態下，所有記憶體指標都應該是水平線。任何斜率 > 0 的指標都是洩漏。**
