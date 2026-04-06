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
*   **啟用大頁記憶體 (Large Pages, 2MB)**：
    *   **Windows**：
        1. 執行 `secpol.msc` 開啟「本機安全性原則」。
        2. 導航至 `本機原則 -> 使用者權限指派 -> 鎖定記憶體中的分頁` (Lock pages in memory)。
        3. 加入執行交易系統的使用者帳號 (如 `Administrators`)。
        4. **重啟電腦後生效**。JVM 參數搭配 `-XX:+UseLargePages`。
    *   **Linux**：配置 `Huge Pages` (2MB/1GB) 並**強烈建議停用 THP (Transparent Huge Pages)**，避免背景整理引發毫秒級卡頓。
*   **分頁檔 (Paging File)**：在實體記憶體充足 (如 32GB+) 時，建議關閉 Windows 分頁檔，防止作業系統將熱點資料 Page Out 導致嚴重延遲。

---

## 3. CPU 親和性與中斷隔離 (Affinity & Interrupts)
將「業務處理」與「系統雜訊」在物理核心上嚴格隔離，是穩定吞吐量的關鍵。

### 3.1 專案核心分配策略 (16 核心範例)
*   **共享核心 / OS 雜訊區 (Core 0, 1, 14, 15)**：處理 OS 中斷、GC (ConcGCThreads)、Netty Boss 等不需微秒級反應的任務。
*   **撮合引擎專屬 (Core 2)**：100% 獨佔，搭配 Busy-Spin，絕不休眠。
*   **Gateway API 專屬 (Core 3-7)**：分配給 2 個 Netty Worker 與 Gateway 內部的 Sender、Receiver、WAL Writer。
*   **基準測試 / 輔助區 (Core 8-11)**：執行壓測腳本或監控工具。
*   **Aeron Media Driver (Core 12, 13)**：專屬負責 IPC 與 UDP 封包的高頻收發。

### 3.2 網卡中斷隔離 (Interrupt Steering)
網卡收到封包時會產生硬體中斷 (MSI-X)，若中斷落在交易核心 (Core 2-7)，將直接打斷正在執行的撮合邏輯。
*   **Windows 實作**：
    1. 下載並以系統管理員執行 [Interrupt Affinity Policy Tool](https://github.com/microsoft/MSI-Utility)。
    2. 找到業務網卡 (Ethernet/Wi-Fi)，點擊 `Set Affinity`。
    3. **僅勾選**共享核心 (0, 1, 14, 15)，取消其他所有核心。
    4. 儲存並重啟。
*   **Linux 實作**：配置 `smp_affinity`，將中斷綁定至隔離區。

---

## 4. 作業系統排程與網路層 (OS & Network)
*   **高精度時鐘 (Timer Resolution)**：Windows 預設為 15.6ms。啟動系統前，需透過 API (如 `NtSetTimerResolution`) 強制將系統時鐘精度鎖定至 **0.5ms**，確保 `Thread.sleep()` 與排程器的高精度喚醒。
*   **電源計畫 (Power Plan)**：切換至「卓越效能」(Ultimate Performance)，防止系統擅自降頻。
*   **網卡高級屬性 (NIC Advanced Properties)**：
    *   **停用「封包合併 (Packet Coalescing)」/「中斷裁減 (Interrupt Moderation)」**：犧牲吞吐量換取極致的低延遲，避免網卡為了批次處理而扣留封包。
    *   **停用「MIMO 省電模式」/「綠色乙太網路 (Green Ethernet)」**：確保網卡全速運作。
*   **磁碟預分配**：啟動前使用 `fsutil` 或 `dd` 預先建立 Aeron WAL 檔案 (如 1GB)，消除執行期間因檔案擴展 (File Extension) 所觸發的 Kernel 態鎖定延遲。
