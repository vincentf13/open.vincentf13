# 作業系統與硬體優化指南 (低延遲交易系統專用)

為確保 Aeron 與撮合引擎能在微秒級（μs）延遲下穩定運行，並徹底消除 P99 抖動（Jitter），必須從 BIOS 到作業系統核心進行深層次的「資源預留」與「干擾隔離」。

---

## 1. BIOS 深度調優 (Jitter 終極消除)
硬體層級的自動節能與降頻是高頻交易系統最大的延遲來源。即使軟體層級實作了「忙轉 (Busy-Spin)」，若硬體觸發休眠，喚醒延遲仍高達 20~100μs。
*   **停用 C-States (C1E, C3, C6, C7)**：強制 CPU 保持在 C0 (滿血) 狀態，徹底消除喚醒延遲。
*   **停用 Intel SpeedStep / Speed Shift (EIST)**：鎖定電壓與頻率，防止因頻率切換造成的指令執行波動。
*   **停用超執行緒 (Hyper-Threading)**：消除兩個邏輯核心競爭同一個 L1/L2 快取所帶來的不確定性，確保指令執行時間恆定。

---

## 2. 記憶體優化 (Memory & TLB)
大量高頻的記憶體映射 (mmap) 存取極易造成 TLB (Translation Lookaside Buffer) Miss。

### 2.1 啟用大頁記憶體 (Large Pages, 2MB)
*   **Windows**：
    1. 執行 `secpol.msc` 開啟「本機安全性原則」。
    2. 導航至 `本機原則 -> 使用者權限指派 -> 鎖定記憶體中的分頁` (Lock pages in memory)。
    3. 加入執行交易系統的使用者帳號 (如 `Administrators`)。
    4. **重啟電腦後生效**。JVM 參數搭配 `-XX:+UseLargePages`。
*   **Linux**：配置 `Huge Pages` (2MB/1GB) 並**強烈建議停用 THP (Transparent Huge Pages)**，避免背景整理引發毫秒級卡頓。

### 2.2 多 JVM 共存的大頁分配策略
Windows 的大頁分配是 **all-or-nothing**：JVM 要嘛拿到全部大頁，要嘛全部 fallback 到 4KB 小頁。多個 JVM 同時啟動時，先搶到的鎖走所有連續 2MB 頁框，後啟動的因記憶體碎片化而失敗。

**解決方案：分階段啟動 + Standby List 三階段清理**

在每個 JVM 啟動前，執行三階段記憶體整合：
1. **EmptyWorkingSets** — 將所有進程的 Working Set 頁框釋回 Standby/Modified List
2. **FlushModifiedList** — 將 Modified 頁框寫入磁碟後釋放為 Free
3. **PurgeStandbyList** — 將 Standby 頁框直接釋放為 Free

清理時機（以本專案為例）：
| 時機 | 目的 |
|------|------|
| Maven 編譯 + Jar 解壓後，Matching Engine 啟動前 | 清掉編譯過程產生的快取碎片 |
| Matching Engine 啟動 5 秒後，Gateway 啟動前 | 清掉 Matching 啟動產生的碎片 |
| 服務穩態 20 秒後，Benchmark 啟動前 | 清掉穩態期間累積的快取 |

> **關鍵**：Windows 不像 Linux 有 `khugepaged` 背景合併小頁，一旦物理記憶體被零散分配，OS 不會主動重組。每次啟動新 JVM 前都必須手動清理。

### 2.3 分頁檔 (Paging File)
在實體記憶體充足 (如 32GB+) 時，建議關閉 Windows 分頁檔，防止作業系統將熱點資料 Page Out 導致嚴重延遲。

---

## 3. CPU 親和性與中斷隔離 (Affinity & Interrupts)
將「業務處理」與「系統雜訊」在物理核心上嚴格隔離，是穩定吞吐量的關鍵。

### 3.1 專案核心分配策略 (16 核心範例)

| 核心 | 用途 | ProcessorAffinity |
|------|------|-------------------|
| Core 0, 1, 14, 15 | 共享區：OS 中斷、GC、JIT、Netty Boss | (包含在各進程 mask 中) |
| Core 2 | 撮合引擎 worker (100% 獨佔 + Busy-Spin) | 49159 (含共享核心) |
| Core 3-7 | Gateway API workers (Netty Worker, Sender, Receiver, WAL Writer) | 49403 (含共享核心) |
| Core 8-11 | 基準測試 / 輔助工具 | 3840 |
| Core 12 | Aeron Media Driver sender (專屬) | 61443 (含共享核心) |
| Core 13 | Aeron Media Driver receiver (專屬) | 61443 (含共享核心) |

> **陷阱**：`ProcessorAffinity` mask 必須包含 Aeron affinity 指定的核心。例如 `-Daeron.receiver.affinity=13` 要求 mask 中包含 bit 13，否則 OS 會將 receiver 線程強制擠到其他核心。

### 3.2 進程優先級策略

| 進程 | PriorityClass | 原因 |
|------|--------------|------|
| Matching Engine | **RealTime** | 延遲最敏感，不可被搶佔 |
| Gateway (WS-API) | **RealTime** | 處理 WebSocket 訂單，延遲直接影響 e2e |
| Aeron Media Driver | **High** | IPC 通道，略低於業務進程 |
| Benchmark | **High** | 需穩定發送速率，但不應搶佔業務 |

> **注意**：不要使用 `Process.MinWorkingSet = Process.MaxWorkingSet` 來鎖定 Working Set。新進程的 `MaxWorkingSet` 預設僅 ~1.4MB，此操作反而會限制進程記憶體，造成嚴重的 Page Fault。Large Pages + AlwaysPreTouch + Chronicle mlock 已足夠防止記憶體被 Trim。

### 3.3 網卡中斷隔離 (Interrupt Steering)
網卡收到封包時會產生硬體中斷 (MSI-X)，若中斷落在交易核心 (Core 2-7)，將直接打斷正在執行的撮合邏輯。
*   **Windows 實作**：
    1. 下載並以系統管理員執行 [Interrupt Affinity Policy Tool](https://github.com/microsoft/MSI-Utility)。
    2. 找到業務網卡 (Ethernet/Wi-Fi)，點擊 `Set Affinity`。
    3. **僅勾選**共享核心 (0, 1, 14, 15)，取消其他所有核心。
    4. 儲存並重啟。
*   **Linux 實作**：配置 `smp_affinity`，將中斷綁定至隔離區。

---

## 4. JVM 關鍵參數 (跨所有服務)
以下參數對低延遲至關重要，所有 JVM 進程均應配置：

| 參數 | 值 | 說明 |
|------|---|------|
| `-XX:+UseZGC` | - | 亞毫秒級 GC 停頓 |
| `-XX:-ZProactive` | - | 關閉 ZGC 主動 GC cycle，避免閒時白耗 CPU |
| `-XX:+UseLargePages` | - | 2MB TLB，減少 TLB Miss |
| `-XX:+AlwaysPreTouch` | - | 啟動時觸摸所有頁框，避免運行時 Page Fault |
| `-XX:+PerfDisableSharedMem` | - | 關閉 `/tmp/hsperfdata_*` 映射，消除檔案系統干擾 |
| `-XX:GuaranteedSafepointInterval=0` | 需 `UnlockDiagnosticVMOptions` | **關閉週期性 Safepoint**。預設 1000ms，每秒強制所有線程進入 safepoint 一次，即使 GC 不需要，造成 10-100μs 停頓 |
| `-XX:-RestrictContended` | - | 允許 `@Contended` 在非 JDK 類別生效 (Cache Line Padding) |

---

## 5. 作業系統排程與網路層 (OS & Network)
*   **高精度時鐘 (Timer Resolution)**：Windows 預設為 15.6ms。啟動系統前，需透過 API (如 `NtSetTimerResolution`) 強制將系統時鐘精度鎖定至 **0.5ms**，確保 `Thread.sleep()` 與排程器的高精度喚醒。
*   **電源計畫 (Power Plan)**：切換至「卓越效能」(Ultimate Performance)，防止系統擅自降頻。若系統未內建此方案，可透過 `powercfg -duplicatescheme e9a42b02-d5df-448d-aa00-03f14749eb61` 建立。
*   **網卡高級屬性 (NIC Advanced Properties)**：
    *   **停用「封包合併 (Packet Coalescing)」/「中斷裁減 (Interrupt Moderation)」**：犧牲吞吐量換取極致的低延遲，避免網卡為了批次處理而扣留封包。
    *   **停用「MIMO 省電模式」/「綠色乙太網路 (Green Ethernet)」**：確保網卡全速運作。
*   **磁碟預分配**：啟動前使用 `fsutil file createnew` 或 `dd` 預先建立 Aeron WAL 檔案 (如 1GB)，消除執行期間因檔案擴展 (File Extension) 所觸發的 Kernel 態鎖定延遲。
