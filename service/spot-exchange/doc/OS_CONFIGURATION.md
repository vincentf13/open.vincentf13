# OS 系統層級配置優化清單 (精簡版)

針對低延遲交易系統，作業系統優化的核心在於「資源預留」與「消除抖動」。

---

### 一、 CPU (處理器)
*   **Windows**
    *   **電源計畫**：切換至「卓越效能」(Ultimate Performance)，防止 CPU 降頻。
    *   **處理器排程**：設定為「背景服務」，獲得更長的時間片 (Quantum)。
    *   **核心停車**：停用 Core Parking (可使用 ParkControl)，確保 P-Core 永不休眠。
*   **Linux**
    *   **核心隔離**：使用 `isolcpus` 參數將交易核心移出排程器。
    *   **頻率調教**：設定 CPU Governor 為 `performance` 模式。

### 二、 內存 (Memory)
*   **Windows**
    *   **鎖定分頁**：授予 `SeLockMemoryPrivilege` 權限以啟用 **Large Pages (2MB)**。
    *   **分頁檔**：若實體記憶體充足，建議停用或極小化 Paging File，防止 Page Out。
*   **Linux**
    *   **大頁記憶體**：配置 `Huge Pages` (2MB/1GB) 減少 TLB Miss。
    *   **透明大頁**：**必須停用** THP (Transparent Huge Pages)，消除背景整理造成的毫秒級卡頓。
    *   **Swappiness**：設為 `0` 或 `1`，避免系統觸發磁碟交換。

### 三、 IO (磁碟與網路)
*   **Windows**
    *   **排除掃描**：將資料與日誌目錄加入 Windows Defender 排除列表。
    *   **預分配**：使用 `fsutil` 預先建立大檔案，消除 Chronicle Queue 寫入時的核心態檔案擴展延遲。
*   **Linux**
    *   **訪問時間**：掛載參數加入 `noatime`，減少 metadata 寫入。
    *   **網路棧**：調整 `rmem_max`/`wmem_max` 並開啟 `busy_poll` 以降低網路收發延遲。

### 四、 減少干擾 (Interference)
*   **Windows**
    *   **中斷優化**：停用網卡「中斷裁減」(Interrupt Moderation)，開啟 RSS。
    *   **優先級**：將核心交易進程設為 `RealTime` 優先級。
    *   **中斷綁定**：使用工具將硬體中斷 (MSI-X) 導向至非交易核心 (如 Core 0, 1)。
*   **Linux**
    *   **無滴答模式**：開啟 `nohz_full` 減少系統時鐘中斷干擾。
    *   **中斷綁定**：設定 `smp_affinity` 將網卡中斷鎖定在特定共享核心。

### 五、 時鐘 (Clock)
*   **Windows**
    *   **解析度**：預設為 15.6ms，壓測期間需強制鎖定在 **0.5ms** (Timer Resolution)。
*   **Linux**
    *   **時鐘源**：確保使用 `tsc` 作為時鐘源。
    *   **同步**：部署 PTP (Precision Time Protocol) 確保微秒級的跨機時序一致。
