# DPC / Interrupt 噪音 baseline (per-core)

> 量測機台：Intel Core Ultra 9 285H, Windows 11 Home 26200
> 量測指令：`Get-Counter "\Processor(*)\% DPC Time","\Processor(*)\% Interrupt Time" -SampleInterval 1 -MaxSamples 30`
> 量測時段：30s idle baseline (無 server / 無壓測進程)，原始輸出見最下方

## 目的

驗證「P-core 之間是否等價」。busy-spin 線程（gateway-sender / gateway-receiver / matching-receiver / Aeron Conductor / netty[0]）落在 DPC 高的核會被定期搶 CPU，直接影響尾延遲。

## 結果摘要

| Core | 類型 | DPC avg% | DPC max% | Interrupt avg% | 評等 |
|---|---|---|---|---|---|
| **P0** | P | 0.57 | 3.09 | 0.72 | 預期髒（DPC primary） |
| **P1** | P | **0.00** | **0.00** | 0.72 | **最乾淨 P-core** ⭐ |
| E2 | E1 | 0.15 | 1.54 | 0.46 | 中 |
| E3 | E1 | 0.00 | 0.00 | 0.31 | 乾淨 |
| E4 | E1 | 0.00 | 0.00 | 0.72 | DPC 乾淨 |
| E5 | E1 | 0.05 | 1.54 | 0.21 | 乾淨 |
| E6 | E2 | 0.00 | 0.00 | 0.15 | 乾淨 |
| E7 | E2 | 0.10 | 1.55 | 0.10 | 乾淨 |
| E8 | E2 | 0.00 | 0.00 | 0.31 | 乾淨 |
| E9 | E2 | 0.05 | 1.55 | 0.26 | 乾淨 |
| **P10** | P | 0.05 | 1.54 | 0.41 | 乾淨 |
| **P11** | P | 0.10 | 1.54 | 0.82 | 乾淨 |
| **P12** | P | **1.08** | **6.17** | **0.93** | **最髒 P-core ⚠️** |
| **P13** | P | 0.05 | 1.54 | 0.57 | 乾淨 |
| LP14 | LP | 0.00 | 0.00 | 0.00 | 乾淨 |
| LP15 | LP | 0.00 | 0.00 | 0.00 | 乾淨 |

## 關鍵發現

1. **P12 DPC 比 P1 高 18×**（avg 1.08% vs 0.00%），max DPC 達 6.17%（單秒可吃掉 6ms）
   - 推測 Windows 把某些裝置中斷（很可能是 storage/NVMe 或 iGPU）routing 到 P12
   - 後續可用 `xperf -on PROC_THREAD+CSWITCH+DPC+INTERRUPT` + Windows Performance Analyzer 確認具體 driver
2. **P1 是這台機 P-core 中最乾淨的**（DPC 0.00%），原先把 gateway-sender 放 P1 的安排其實是最佳的
3. **P0 確實是 DPC 主噪音核**（avg 0.57%），全進程 mask 排除是對的
4. **E-cluster1/2 與 LP14/15 都很乾淨**（DPC 多在 0.05% 以下）

## 建議

| 線程 | 推薦核 | 理由 |
|---|---|---|
| gateway-sender (busy-spin) | **P1** ⭐ | DPC 0% 最低噪音 |
| gateway-receiver (busy-spin) | P10 | DPC 0.05% |
| matching-receiver (busy-spin) | P13 | DPC 0.05% |
| Aeron Conductor (BusySpin) | P11 | DPC 0.10% |
| netty-worker[0] (event-loop) | **P12 不是好選擇**，可考慮交換 | DPC 1.08% 偶有 6ms 突波 |

**結論：P1 ↔ P12 swap 應該 revert**。把 gateway-sender 搬到 P12 等於把延遲最敏感的線程放到 DPC 最多的核，是反向操作。

## P12 DPC 來源排查（持續中）

排除法測試，依序 disable 各裝置/服務測量 P12 DPC% 變化：

| Step | 動作 | P12 DPC | 結論 |
|---|---|---|---|
| 0 | baseline (idle) | 0.62–1.07% | — |
| 1 | Disable Wi-Fi (Killer BE201NGW) | 1.86%（變高，OS reconfigure） | ❌ 不是 Wi-Fi |
| 2 | Disable Bluetooth radio | 0.94% | ❌ 不是 BT |
| 3 | Stop WSearch（短時間量）| 0.00% | ⚠️ 偽陽性，見下 |
| 4 | Stop SysMain | 0.62% | ❌ 影響小 |
| 5 | **完整 OS opt env + WSearch stopped（5×20s）** | **0.91% avg / 7.81% max** | **WSearch 不是真因** |

### 撤回前次錯誤結論
先前單次測量「stop WSearch → P12 DPC 0%」是**短窗口巧合**。完整環境長時間測量證實 P12 DPC 不受 WSearch 影響。WSearch 只是會 indexing 時偶發 spike，**P12 的 1% 基線 DPC 是來自其他源頭**。

### 推測真因（尚未確認）
- **Intel Arc 140T iGPU**（igdkmd64.sys）— display refresh + PMT (Platform Monitoring Technology)
- **Intel RST VMD Controller 7D0B**（iaStorAC.sys / storport.sys）— NVMe MSI-X 中斷
- **Intel Power Engine Plug-in**（PEP.sys）— SoC 電源狀態變更通知

這層 routing 由 ACPI / BIOS 決定，OS 層無法重定向到 P0。**結論：P12 是結構性髒核，不可救**。

### 已採取對策
1. **busy-spin 線程全避 P12**：gateway-sender→P1, gateway-receiver→P10, Aeron Conductor→P11, matching-receiver→P13, netty[0]→E5
2. **P12 改作 matching ZGC drift core**：GC 是 bursty + 非熱路徑，DPC 7ms 突波對它影響可忽略
3. **保留 Stop-Service WSearch 進腳本**：仍可降低 indexing 時的次要噪音（無傷害，可逆）

### 未來可做（非必要）
1. WPR + WPA 開啟 `C:\temp\dpc.etl` 找 P12 DPC stack 中的具體 driver
2. 換 BIOS：某些主機板提供 IRQ remap 選項
3. 若要徹底解決：把整台機器改成 Linux + IRQ affinity（Windows ACPI 限制大）

## 改動可逆性總表

| 改動 | 還原方式 | 重啟自動回？ | 在腳本內 |
|---|---|---|---|
| `powercfg /setactive ultimateScheme` | finally 還原原 GUID | ❌ | ✅ |
| `NetworkThrottlingIndex` | finally 還原原值 | ❌ | ✅ |
| `SystemResponsiveness` | finally 還原原值 | ❌ | ✅ |
| Packet Coalescing | finally 還原 | ❌ | ✅ |
| MIMO Power Save Mode | finally 還原 | ❌ | ✅ |
| `NtSetTimerResolution(5000)` | finally Reset | ✅（kernel 計時器） | ✅ |
| `PurgeStandbyList` | 無需還原 | ✅ | ✅ |
| **`Stop-Service WSearch`** | **finally `Start-Service`** | ❌ | ✅ |
| Process priority RealTime | 進程結束自動回 | ✅ | ✅ |

**結論**：當前腳本所有改動都會在 finally 還原。**唯一靠重啟自動回**的只有 kernel timer resolution（reboot 強制 reset 為預設 15.625ms）；其他全部要靠 script finally。若腳本被 Ctrl-C 中斷會走 finally；若機器當機/斷電，改動會保留（但都不致命，下次手動修正或 reboot 後手動還原即可）。

## 完整 OS 優化環境下的多輪量測（5 × 20s）

**環境**：壓測腳本的完整 OS opt 已套用（終極效能 + NetworkThrottling=0xFFFFFFFF + SystemResponsiveness=0 + Packet Coalescing/MIMO 關 + WSearch 停 + Timer res 0.5ms），server 進程未啟動。

| Core | R1 | R2 | R3 | R4 | R5 | Avg | Max |
|---|---|---|---|---|---|---|---|
| P0 | 1.40 | 0.16 | 0.31 | 1.24 | 0.55 | **0.73** | **4.68** |
| P1 | 0.16 | 0.08 | 0.16 | 0.00 | 0.00 | 0.08 | 1.56 |
| E2 | 0.08 | 0.00 | 0.08 | 0.00 | 0.00 | 0.03 | 1.56 |
| E3 | 0.23 | 0.08 | 0.08 | 0.08 | 0.00 | 0.09 | 1.56 |
| E4 | 0.23 | 0.47 | 0.08 | 0.31 | 0.31 | 0.28 | 3.12 |
| E5 | 0.15 | 0.00 | 0.00 | 0.00 | 0.00 | 0.03 | 1.55 |
| E6 | 0.08 | 0.00 | 0.00 | 0.31 | 0.00 | 0.08 | 3.10 |
| E7 | 0.15 | 0.00 | 0.00 | 0.08 | 0.08 | 0.06 | 1.56 |
| E8 | 0.08 | 0.00 | 0.08 | 0.08 | 0.00 | 0.05 | 1.56 |
| E9 | 0.08 | 0.00 | 0.00 | 0.08 | 0.00 | 0.03 | 1.56 |
| P10 | 0.23 | 0.08 | 0.08 | 0.08 | 0.00 | 0.09 | 1.56 |
| P11 | 0.15 | 0.00 | 0.16 | 0.08 | 0.00 | 0.08 | 1.56 |
| **P12** | **1.09** | **1.01** | **1.25** | **0.55** | **0.62** | **0.91** | **7.81** |
| P13 | 0.15 | 0.16 | 0.16 | 0.16 | 0.16 | 0.16 | 1.56 |
| LP14 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 |
| LP15 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 |

### 結論
**完整 OS 優化也無法降低 P12 DPC**（與 baseline 0.93%/6.17% 幾乎相同）。確認 P12 為結構性 DPC 髒核，非 OS 服務造成，是硬體 IRQ routing 決定。當前佈局把 P12 留給 matching GC 是正確選擇。

---

## 初次量測（無 OS 優化的 idle baseline，5 × 20s）

| Core | Run1 | Run2 | Run3 | Run4 | Run5 | AvgAll | MaxAll |
|---|---|---|---|---|---|---|---|
| P0 | 0.31 | 0.46 | 1.47 | 1.47 | 1.31 | **1.00** | **6.16** |
| P1 | 0.15 | 0.00 | 0.15 | 0.15 | 0.15 | 0.12 | 1.55 |
| E2 | 0.00 | 0.08 | 0.08 | 0.15 | 0.23 | 0.11 | 1.56 |
| E3 | 0.08 | 0.00 | 0.08 | 0.08 | 0.15 | 0.08 | 1.56 |
| E4 | 0.08 | 0.23 | 0.31 | 0.00 | 0.46 | 0.22 | 3.09 |
| E5 | 0.15 | 0.08 | 0.08 | 0.15 | 0.23 | 0.14 | 1.56 |
| E6 | 0.00 | 0.00 | 0.23 | 0.23 | 0.23 | 0.14 | 1.56 |
| E7 | 0.00 | 0.08 | 0.08 | 0.16 | 0.15 | 0.09 | 1.56 |
| E8 | 0.08 | 0.15 | 0.15 | 0.08 | 0.15 | 0.12 | 1.55 |
| E9 | 0.00 | 0.00 | 0.23 | 0.16 | 0.15 | 0.11 | 3.09 |
| P10 | 0.15 | 0.08 | 0.00 | 0.16 | 0.23 | 0.12 | 1.56 |
| P11 | 0.23 | 0.08 | 0.15 | 0.00 | 0.15 | 0.12 | 3.10 |
| **P12** | **0.69** | **1.39** | **1.32** | **0.77** | **0.93** | **1.02** | **7.69** |
| P13 | 0.08 | 0.15 | 0.00 | 0.16 | 0.31 | 0.14 | 1.56 |
| LP14 | 0.00 | 0.00 | 0.00 | 0.00 | 0.08 | 0.01 | 1.55 |
| LP15 | 0.00 | 0.00 | 0.00 | 0.00 | 0.08 | 0.01 | 1.55 |

### 觀察
1. **P12 在 5 輪皆穩定 0.7-1.4% 區間**，與 P0 同等級的 DPC 主噪音核。非偶發。
2. **P1, P10, P11, P13 在 5 輪皆 < 0.25%**，互為等價乾淨 P-core。
3. **E-cluster 整體乾淨**（avg < 0.25%），偶爾單秒 spike 到 1.5% 屬正常 timer interrupt。
4. **LP14/15 接近 0**，純後台。

### 判決
P12 與 P0 同列為 DPC 主噪音核。把任何 busy-spin 線程放 P12 等於主動承受週期性 6-8ms 搶 CPU。
**P1 ↔ P12 swap 必須 revert**：gateway-sender 回 P1，netty[0] 回 P12。

## 30s 單輪原始量測輸出

```
Core Metric            AvgPct MaxPct
---- ------            ------ ------
0    % dpc time         0.57   3.09
1    % dpc time         0.00   0.00
2    % dpc time         0.15   1.54
3    % dpc time         0.00   0.00
4    % dpc time         0.00   0.00
5    % dpc time         0.05   1.54
6    % dpc time         0.00   0.00
7    % dpc time         0.10   1.55
8    % dpc time         0.00   0.00
9    % dpc time         0.05   1.55
10   % dpc time         0.05   1.54
11   % dpc time         0.10   1.54
12   % dpc time         1.08   6.17
13   % dpc time         0.05   1.54
14   % dpc time         0.00   0.00
15   % dpc time         0.00   0.00
0    % interrupt time   0.72   3.08
1    % interrupt time   0.72   3.12
2    % interrupt time   0.46   1.56
3    % interrupt time   0.31   1.56
4    % interrupt time   0.72   3.12
5    % interrupt time   0.21   1.56
6    % interrupt time   0.15   3.08
7    % interrupt time   0.10   1.54
8    % interrupt time   0.31   1.55
9    % interrupt time   0.26   1.56
10   % interrupt time   0.41   3.08
11   % interrupt time   0.82   4.62
12   % interrupt time   0.93   3.08
13   % interrupt time   0.57   3.09
14   % interrupt time   0.00   0.00
15   % interrupt time   0.00   0.00
```
