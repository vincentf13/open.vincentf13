# Open Exchange Core - 虛擬貨幣合約交易所核心系統

本專案是一個專為高併發、低延遲場景設計的金融交易核心系統。透過自主研發的分散式協議與內存撮合技術，解決了現代交易所面臨的一致性、擴展性與稽核痛點。

## 🚀 核心技術特性

### ⚡ 極速撮合與事務鏈路
- **高併發核心架構**：設計並實現撮合、資產結算、風控與事務一致性的核心鏈路。
- **LMAX 確定性撮合引擎**：採用 **LMAX Disruptor** 架構模式，透過無鎖隊列（Lock-free Buffer）極大化單核吞吐量，達成微秒級的確定性撮合延遲。
- **單次 WAL 寫入與快照恢復**：優化 IO 鏈路實現**單次 WAL (Write-Ahead Logging) 順序寫入**，在確保每一筆指令強持久化的同時，將磁碟 IO 對延遲的影響降至最低，並結合快照機制達成秒級容災恢復。
- **架構解耦與擴展**：支援行情、風控與資產服務的完全解耦，可根據負載需求橫向擴展交易集群。
- [WAL 容災測試 Demo](https://www.youtube.com/watch?v=ZWwiQsdZz84)
- [自動化整合測試交易鏈路 Demo](https://www.youtube.com/watch?v=dn5fbFdlFOQ)

### 💰 金融級會計與動態資產負債建模
- **動態資產負債表建模**：內置交易所級金流會計模組，支援多種數位資產動態建模，實時追蹤每一筆資產與負債變動。此架構不僅能發現問題，更能透過數據關聯主動定位異常，將「事後審計」轉化為「即時風控」。
- **多維度自動對帳（Future-Ready）**：系統已建置深層數據關聯基礎，支持未來擴展至**多維度自動對帳**（包含撮合流水、錢包餘額、帳務分錄與第三方結算的多向核對），實現全自動化的數據一致性自癒機制。
- **專業審計與查帳賦能**：提供專業會計團隊所需的查帳工具，支持利用複式簿記技巧進行深度追蹤。可重建任一時刻的完整財務快照，確保 100% 數據完整性與可追溯性，滿足最嚴苛的金融審計標準。
- **全場景帳戶視圖**：針對多幣種、多倉位資產提供直觀的全景展示視圖。無論是會計團隊的專業複核，還是交易所內部的資產管理，皆能透過高度結構化的帳戶體系達成秒級對帳，大幅優化人工稽核成本。
- Demo: https://www.youtube.com/watch?v=MYQobecR8DA    

### 🛡️ 分散式一致性 (Flip Protocol)
- [設計並實作基於 Flip 邏輯的分佈式事務協議，專門處理分散式環境下的 Stealing（資源衝突/竊取）情況](https://youtu.be/R9S6q3e9xgw)，確保多節點間強一致性與交易原子性
- **強一致性保證**：確保多節點併發環境下的交易原子性，維持金融數據的絕對準確。

### ☸️ 雲原生自動化運維
- **高效開發環境**：極致的開發體驗，[一鍵 K8s 集群快速建置、IDEA直連K8S、全環境統一配置、智能 CI/CD 流程](https://www.youtube.com/watch?v=kvOtuF93q2s&t=1896s)
- **極致資源控制**：經過深層優化，整個集群僅需[ **8.3 GB 內存**](https://onedrive.live.com/?photosData=%2Fshare%2F095E0F59106ABB25%21sca5579b81933414b846320a6b1f53462%3Fithint%3Dphoto%26e%3D2ccROi%26migratedtospo%3Dtrue&redeem=aHR0cHM6Ly8xZHJ2Lm1zL2kvYy8wOTVlMGY1OTEwNmFiYjI1L0lRQzRlVlhLTXhsTFFZUmpJS2F4OVRSaUFlbTZHbGRaQ3BOTC01SWdYbThHejJBP2U9MmNjUk9p&view=8) 即可穩定運行。
- **全面觀測能力**：內置自動化整合測試，並集成[監控](https://www.youtube.com/watch?v=t4foO-PD3eI)、告警與自動擴容機制，覆蓋複雜撮合情境與資產帳戶核對。