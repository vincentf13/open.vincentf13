# 低延遲 OS 配置指南

## 一次性設定 (需重啟)

### 1. BIOS
| 設定 | 動作 | 原因 |
|------|------|------|
| C-States (C1E, C3, C6, C7) | 停用 | 核心休眠喚醒延遲 20~100μs |
| Intel SpeedStep / Speed Shift | 停用 | 頻率切換造成指令執行波動 |
| Hyper-Threading | 停用 | 邏輯核心競爭 L1/L2 快取 |

### 2. Large Pages 權限
`secpol.msc` → `本機原則 → 使用者權限指派 → 鎖定記憶體中的分頁` → 加入使用者帳號 → **重啟**

### 3. Windows Defender 排除
Defender minifilter 在**業務線程的 kernel context 同步攔截** I/O，核心隔離擋不住，延遲可達 1-5ms。
```powershell
Add-MpPreference -ExclusionPath "C:\iProject\open.vincentf13\data\spot-exchange"
```
或壓測時直接關閉即時保護。

### 4. 停用記憶體壓縮
Windows 背景壓縮記憶體頁，解壓發生在業務線程 context，造成不可預測的 P99 毛刺。
```powershell
Disable-MMAgent -MemoryCompression  # 需重啟
```

### 5. 網卡中斷隔離
用 [Interrupt Affinity Policy Tool](https://github.com/microsoft/MSI-Utility) 將網卡中斷**僅綁定到共享核心 (0, 1, 14, 15)**，防止 MSI-X 中斷打斷業務核心。

---

## 壓測腳本自動處理 (啟動時設定 / 結束後還原)

以下由 `gemini_stress_test_final.ps1` 自動管理，無需手動操作：

| 項目 | 壓測時 | 結束後 |
|------|--------|--------|
| 高精度時鐘 | `NtSetTimerResolution(5000)` → 0.5ms | 還原預設 |
| 電源計畫 | 切換至「卓越效能」 | 還原原方案 |
| 網卡 Packet Coalescing | Disabled | 還原 |
| 網卡 MIMO Power Save | No SMPS | 還原 |
| NetworkThrottlingIndex | 0xFFFFFFFF (停用限流) | 還原原值 |
| SystemResponsiveness | 0 (最小化背景 CPU) | 還原原值 |
| Standby List 清理 | 每個 JVM 啟動前三階段清理 | - |

---

## 核心分配 (16 核心)

| 核心 | 用途 | ProcessorAffinity |
|------|------|-------------------|
| 0, 1, 14, 15 | 共享：OS 中斷、GC、JIT | (含在各進程 mask 中) |
| 2 | Matching Engine (Busy-Spin 獨佔) | 49159 |
| 3-7 | Gateway workers | 49403 |
| 8-11 | Benchmark | 3840 |
| 12, 13 | Aeron Media Driver (sender, receiver) | 61443 |

**陷阱**：`ProcessorAffinity` mask 必須包含 Aeron affinity 指定的核心。`-Daeron.receiver.affinity=13` 但 mask 不含 bit 13 → receiver 被擠到別的核心。

---

## JVM 關鍵參數

所有 JVM 進程均須配置：

| 參數 | 說明 |
|------|------|
| `-XX:+UseZGC -XX:-ZProactive` | ZGC + 關閉主動 GC cycle |
| `-XX:+UseLargePages -XX:+AlwaysPreTouch` | 2MB 大頁 + 啟動時觸摸所有頁框 |
| `-XX:+PerfDisableSharedMem` | 關閉 hsperfdata 映射 |
| `-XX:+UnlockDiagnosticVMOptions -XX:GuaranteedSafepointInterval=0` | **關閉週期性 Safepoint** (預設每秒一次，10-100μs 停頓) |
| `-XX:-RestrictContended` | 允許 @Contended Cache Line Padding |

---

## 進程優先級

| 進程 | PriorityClass |
|------|--------------|
| Matching / Gateway | **RealTime** |
| Aeron Driver / Benchmark | **High** |

> **不要用** `Process.MinWorkingSet = Process.MaxWorkingSet` 鎖 Working Set。新進程 `MaxWorkingSet` 預設 ~1.4MB，反而限制記憶體。Large Pages + mlock 已足夠。

---

## Large Pages 分配策略

`-XX:+UseLargePages` 只影響 **JVM heap / metaspace / code cache**。Chronicle WAL 的 file-backed mmap 在 Windows 上一律使用 4KB 頁，不受此參數影響。

32GB 機器上只有 **Gateway 啟用 Large Pages**：
- Gateway：Netty buffer pool + 多連線並行 heap 存取較散佈，TLB miss 收益大
- Matching Engine：order book working set 小且反覆存取，TLB 壓力本來就低

Windows 大頁分配是 **all-or-nothing**，且不會背景合併碎片。啟動順序：
1. Matching Engine 先啟動（不用大頁）+ Chronicle mlock 完成
2. **Standby List 三階段清理**（EmptyWorkingSets → FlushModified → PurgeStandby）
3. Gateway 啟動，拿到乾淨的大頁池
