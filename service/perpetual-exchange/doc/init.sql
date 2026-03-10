-- Database Initialization Script
-- Compiled from design documents

-- ==========================================
-- 1. Core Tables (Users, Instruments)
-- ==========================================

-- users - 使用者主檔
CREATE TABLE `users`
(
    `id`          BIGINT       NOT NULL COMMENT '平台內部使用者主鍵',
    `external_id` VARCHAR(64)           DEFAULT NULL COMMENT '外部系統引用用戶 ID（可選）',
    `email`       VARCHAR(255) NOT NULL COMMENT '登入/通知用 Email，唯一',
    `status`      VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '使用者狀態（ACTIVE / LOCKED / DISABLED）',
    `created_at`  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建立時間',
    `updated_at`  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最後更新時間',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_email` (`email`),
    UNIQUE KEY `uk_users_external` (`external_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '使用者主檔';

CREATE INDEX idx_status ON users (status) COMMENT '查詢特定狀態用戶';
CREATE INDEX `idx_created_at` ON `users` (`created_at` DESC) COMMENT '按註冊時間排序';

-- instrument - 交易商品設定
CREATE TABLE instrument
(
    instrument_id    BIGINT         NOT NULL COMMENT '交易對ID (Snowflake)',
    symbol           VARCHAR(50)    NOT NULL COMMENT '交易對代碼 (如 BTCUSDT, ETHUSDT)',
    name             VARCHAR(100)   NOT NULL COMMENT '交易對名稱',
    base_asset       VARCHAR(20)    NOT NULL COMMENT '基礎資產 (如 BTC, ETH)',
    quote_asset      VARCHAR(20)    NOT NULL COMMENT '計價資產 (如 USDT, USD)',
    instrument_type  VARCHAR(20)    NOT NULL COMMENT '商品類型: SPOT(現貨), PERPETUAL(永續合約), FUTURES(期貨), OPTION(選擇權)',
    status           VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE' COMMENT '狀態: ACTIVE, SUSPENDED, DELISTED, COMING_SOON',

    # 手續費
    maker_fee_rate   DECIMAL(10, 4) NOT NULL DEFAULT 0.0002 COMMENT 'Maker手續費率 (如 0.0002 = 0.02%)',
    taker_fee_rate   DECIMAL(10, 4) NOT NULL DEFAULT 0.0005 COMMENT 'Taker手續費率 (如 0.0005 = 0.05%)',

    # 合約配置
    contract_size    DECIMAL(20, 8) NULL COMMENT '合約面值 (合約專用)',
    default_leverage INT            NOT NULL DEFAULT 4 COMMENT '預設槓桿',

    launch_at        DATETIME       NOT NULL COMMENT '上線時間',
    delist_at        DATETIME       NULL COMMENT '下線時間 (NULL表示未下線)',
    display_order    INT            NOT NULL DEFAULT 0 COMMENT '前端顯示排序 (數字越小越靠前)',
    is_tradable      BOOLEAN        NOT NULL DEFAULT TRUE COMMENT '是否可交易',
    is_visible       BOOLEAN        NOT NULL DEFAULT TRUE COMMENT '是否前端可見',
    description      TEXT           NULL COMMENT '商品描述',
    metadata         JSON           NULL COMMENT '額外配置 (風險參數、特殊規則等)',
    created_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '創建時間',
    updated_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新時間',
    PRIMARY KEY (instrument_id),
    UNIQUE KEY uk_symbol (symbol) COMMENT '交易對代碼唯一'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='交易商品設定表 - 供所有交易模組共用的靜態資訊';

CREATE INDEX idx_status_tradable ON instrument (status, is_tradable, display_order) COMMENT '查詢可交易商品列表';
CREATE INDEX idx_base_asset ON instrument (base_asset, status) COMMENT '按基礎資產查詢';
CREATE INDEX idx_quote_asset ON instrument (quote_asset, status) COMMENT '按計價資產查詢';
CREATE INDEX idx_instrument_type ON instrument (instrument_type, status) COMMENT '按商品類型查詢';
CREATE INDEX `idx_launch_at` ON `instrument` (`launch_at` DESC) COMMENT '按上線時間排序';
CREATE INDEX idx_display_order ON instrument (display_order ASC, symbol) COMMENT '前端顯示排序';

-- ==========================================
-- 2. Auth Related Tables
-- ==========================================

-- auth_credentials - 登入憑證資料
CREATE TABLE `auth_credentials`
(
    `id`              BIGINT       NOT NULL COMMENT '憑證主鍵',
    `user_id`         BIGINT       NOT NULL COMMENT '對應使用者 ID',
    `credential_type` VARCHAR(32)  NOT NULL COMMENT '憑證型別（PASSWORD / API_KEY / FIDO 等）',
    `secret_hash`     VARCHAR(255) NOT NULL COMMENT '憑證雜湊值',
    `salt`            VARCHAR(255) NOT NULL COMMENT '雜湊鹽值',
    `status`          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '憑證狀態',
    `expires_at`      TIMESTAMP    NULL COMMENT '可選的到期時間',
    `created_at`      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建立時間',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_auth_credentials_user_type` (`user_id`, `credential_type`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '登入憑證資料表';

CREATE INDEX idx_user_id ON auth_credentials (user_id) COMMENT '查詢用戶所有憑證';
CREATE INDEX idx_status ON auth_credentials (status) COMMENT '查詢特定狀態憑證 (如LOCKED)';
CREATE INDEX idx_expires_at ON auth_credentials (expires_at) COMMENT '定期清理過期憑證';

-- ==========================================
-- 3. Accounting Tables
-- ==========================================

-- platform_accounts - 平台總帳
CREATE TABLE platform_accounts
(
    account_id   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '平台帳戶ID',

    # --- 核心識別 ---
    account_code VARCHAR(64)     NOT NULL COMMENT '科目代碼 (如: HOT_WALLET, FEE_REVENUE, USER_LIABILITY)',
    account_name VARCHAR(128)    NOT NULL COMMENT '科目名稱',

    # --- 會計屬性 ---
    # ASSET(資產), LIABILITY(負債), EQUITY(權益), REVENUE(收入), EXPENSE(支出)
    category     VARCHAR(32)     NOT NULL,
    asset        VARCHAR(20)     NOT NULL COMMENT '幣種',

    # --- 資金狀態 ---
    balance      DECIMAL(30, 18) NOT NULL DEFAULT 0 COMMENT '總餘額',

    # --- 系統 ---
    version      INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '樂觀鎖',
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (account_id),
    UNIQUE KEY uk_code_asset (account_code, asset),
    INDEX idx_category_asset (category, asset)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='平台總帳科目表';

-- user_accounts - 用戶帳戶表
CREATE TABLE user_accounts
(
    account_id    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用戶帳戶ID',
    user_id       BIGINT UNSIGNED NOT NULL COMMENT '用戶ID',

    # --- 核心識別 ---
    # 業務代碼: SPOT, MARGIN, FEE_EXPENSE, FUNDING_INCOME
    account_code  VARCHAR(32)     NOT NULL,
    account_name  VARCHAR(64)     NOT NULL,
    instrument_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '關聯交易對ID (僅逐倉或特定業務需要，0 代表現貨帳戶)',

    # --- 會計屬性 ---
    category      VARCHAR(20)     NOT NULL COMMENT 'ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE',
    asset         VARCHAR(20)     NOT NULL,

    # --- 資金狀態 ---
    balance       DECIMAL(30, 18) NOT NULL DEFAULT 0 COMMENT '總權益 (Available + Reserved)',
    available     DECIMAL(30, 18) NOT NULL DEFAULT 0 COMMENT '可用餘額',
    reserved      DECIMAL(30, 18) NOT NULL DEFAULT 0 COMMENT '凍結金額',

    # --- 系統 ---
    version       INT UNSIGNED    NOT NULL DEFAULT 0,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (account_id),
    UNIQUE KEY uk_user_code_asset_cat (user_id, account_code, instrument_id, asset, category),
    INDEX idx_user_asset (user_id, asset)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用戶明細帳戶表';

-- platform_journal - 平台日記帳
CREATE TABLE platform_journal
(
    journal_id     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流水號',
    account_id     BIGINT UNSIGNED NOT NULL COMMENT '關聯 platform_accounts',

    # --- 財務快照 ---
    category       VARCHAR(32)     NOT NULL,

    # --- 變動 ---
    asset          VARCHAR(20)     NOT NULL,
    amount         DECIMAL(30, 18) NOT NULL COMMENT '絕對值',
    direction      VARCHAR(10)     NOT NULL COMMENT 'DEBIT, CREDIT',
    balance_after  DECIMAL(30, 18) NOT NULL,

    # --- 溯源 ---
    reference_type VARCHAR(50)     NOT NULL COMMENT '關聯業務類型',
    reference_id   VARCHAR(100)    NOT NULL COMMENT '關聯業務ID',
    seq            INT             NOT NULL COMMENT '交易內序號',
    description    VARCHAR(255)    NULL COMMENT '備註',

    event_time     DATETIME        NOT NULL,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (journal_id),
    INDEX idx_ref (reference_type, reference_id),
    INDEX `idx_account_time` (`account_id`, `event_time` DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='平台日記帳';

-- user_journal - 用戶日記帳
CREATE TABLE user_journal
(
    journal_id     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流水號',
    user_id        BIGINT UNSIGNED NOT NULL COMMENT '分片鍵',
    account_id     BIGINT UNSIGNED NOT NULL COMMENT '關聯 user_accounts',

    # --- 財務快照 ---
    category       VARCHAR(20)     NOT NULL,

    # --- 變動 ---
    asset          VARCHAR(20)     NOT NULL,
    amount         DECIMAL(30, 18) NOT NULL COMMENT '絕對值',
    direction      VARCHAR(10)     NOT NULL COMMENT 'DEBIT, CREDIT',
    balance_after  DECIMAL(30, 18) NOT NULL,

    # --- 溯源 ---
    reference_type VARCHAR(50)     NOT NULL COMMENT '關聯業務類型',
    reference_id   VARCHAR(100)    NOT NULL COMMENT '關聯業務ID',
    seq            INT             NOT NULL COMMENT '交易內序號',
    description    VARCHAR(255)    NULL COMMENT '備註',

    event_time     DATETIME        NOT NULL,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (journal_id),
    INDEX `idx_user_account_time` (`user_id`, `account_id`, `event_time` DESC),
    INDEX idx_ref (reference_type, reference_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用戶日記帳';

-- ==========================================
-- 4. Order & Trade Tables
-- ==========================================

-- orders - 訂單主檔
CREATE TABLE orders
(
    order_id           BIGINT         NOT NULL COMMENT '訂單ID (Snowflake)',
    user_id            BIGINT         NOT NULL COMMENT '用戶ID (外鍵關聯 users.id)',
    instrument_id      BIGINT         NOT NULL COMMENT '交易對ID (外鍵關聯 instruments.id)',
    client_order_id    VARCHAR(64)    NULL COMMENT '客戶端訂單ID (用於冪等性去重)',

    # 訂單
    side               VARCHAR(10)    NOT NULL COMMENT '訂單方向: BUY, SELL.  開多/平空=BUY, 開空/平多=賣',
    type               VARCHAR(20)    NOT NULL COMMENT '訂單類型: LIMIT, MARKET, STOP_LIMIT, STOP_MARKET',
    price              DECIMAL(20, 8) NULL COMMENT '委託價格 (市價單為 NULL)',
    quantity           DECIMAL(20, 8) NOT NULL COMMENT '委託數量',

    # 平倉
    intent             VARCHAR(20)    NULL COMMENT '開倉/減倉/平倉意圖: INCREASE, REDUCE, CLOSE，由 position 服務判斷後回寫',


    # 成交
    filled_quantity    DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '已成交數量',
    remaining_quantity DECIMAL(20, 8) NOT NULL COMMENT '剩餘數量 (quantity - filled_quantity)',
    avg_fill_price     DECIMAL(20, 8) NULL COMMENT '平均成交價格',
    fee                DECIMAL(20, 8) NULL COMMENT '手續費總額',

    # 狀態
    status             VARCHAR(20)    NOT NULL DEFAULT 'CREATED' COMMENT '訂單狀態: CREATED, LOCKING_POSITION / FREEZING_MARGIN / FLIP_CALCULATING / FLIP_LOCKING_ASSETS (預處理), NEW, PARTIALLY_FILLED, FILLED, CANCELLING, CANCELLED, REJECTED',
    rejected_reason    VARCHAR(255)   NULL COMMENT '拒絕原因 (風控/餘額不足等)',

    # 時間
    created_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '創建時間',
    updated_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新時間',
    submitted_at       DATETIME       NULL COMMENT '提交到撮合引擎時間',
    filled_at          DATETIME       NULL COMMENT '完全成交時間',
    cancelled_at       DATETIME       NULL COMMENT '取消時間',

    version            INT            NOT NULL DEFAULT 0 COMMENT '樂觀鎖版本號 (併發控制)',

    PRIMARY KEY (order_id),
    UNIQUE KEY uk_client_order (user_id, client_order_id) COMMENT '防止客戶端重複提交'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='訂單主檔 - 委託生命週期核心資料';

CREATE INDEX idx_user_status ON orders (user_id, status) COMMENT '查詢用戶活動訂單';
CREATE INDEX idx_instrument_status ON orders (instrument_id, status) COMMENT '查詢交易對活動訂單';
CREATE INDEX idx_status_created ON orders (status, created_at) COMMENT '按狀態與時間查詢 (如待撮合訂單)';
CREATE INDEX `idx_created_at` ON `orders` (`created_at` DESC) COMMENT '歷史訂單時序查詢';

-- order_events - 訂單事件溯源表
CREATE TABLE order_events
(
    event_id        BIGINT       NOT NULL COMMENT '事件ID (Snowflake)',
    user_id         BIGINT       NOT NULL COMMENT '用戶ID (冗餘欄位便於查詢)',
    instrument_id   BIGINT       NOT NULL COMMENT '交易對ID (冗餘欄位便於查詢)',
    order_id        BIGINT       NOT NULL COMMENT '訂單ID (外鍵關聯 orders.order_id)',

    # 事件    
    event_type      VARCHAR(80)  NOT NULL COMMENT '事件類型',
    sequence_number BIGINT       NOT NULL COMMENT '同一訂單內的事件序號 (確保順序性)',

    payload         JSON         NOT NULL COMMENT '事件內容 (狀態變更的具體數據)',

    # 來源
    reference_type  VARCHAR(50)  NULL COMMENT '用來描述這筆事件是因何而發生、關聯到哪個外部實體或動作。通常用於冪等性控制 或 跨系統追蹤: TRADE, CANCEL_REQUEST, RISK_CHECK ..',
    reference_id    VARCHAR(128) NULL COMMENT '關聯ID (trade_id 等, 用於冪等性檢查)',
    actor           VARCHAR(100) NOT NULL COMMENT '操作人: USER:{userId}, RISK_SERVICE, LEDGER_SERVICE, MATCHING_ENGINE, SYSTEM(定時或自動規則不屬於特定服務)',


    occurred_at     DATETIME(6)  NOT NULL COMMENT '原始事件的「真實發生時間」 (微秒精度)',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '記錄創建時間',
    PRIMARY KEY (event_id),
    UNIQUE KEY uk_order_sequence (order_id, sequence_number) COMMENT '確保同一訂單事件順序唯一',
    UNIQUE KEY uk_idempotent (order_id, reference_type, reference_id) COMMENT '防止重複處理同一業務事件 (reference_id 非 NULL 時)'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='訂單事件溯源表 - Event Sourcing 模式';

CREATE INDEX idx_order_occurred ON order_events (order_id, occurred_at ASC) COMMENT '查詢訂單事件歷史 (按時間順序)';
CREATE INDEX `idx_user_occurred` ON `order_events` (`user_id`, `occurred_at` DESC) COMMENT '查詢用戶訂單事件';
CREATE INDEX `idx_event_type_occurred` ON `order_events` (`event_type`, `occurred_at` DESC) COMMENT '按事件類型統計分析';
CREATE INDEX `idx_actor` ON `order_events` (`actor`, `occurred_at` DESC) COMMENT '按操作主體查詢';
CREATE INDEX idx_reference ON order_events (reference_type, reference_id) COMMENT '通過業務關聯ID查找事件';
CREATE INDEX `idx_occurred_at` ON `order_events` (`occurred_at` DESC) COMMENT '時序查詢所有訂單事件';

-- trade - 成交紀錄表
CREATE TABLE trade
(
    trade_id                           BIGINT         NOT NULL COMMENT '成交ID (Snowflake)',
    instrument_id                      BIGINT         NOT NULL COMMENT '交易對ID (外鍵關聯 instruments.id)',

    # 訂單
    maker_user_id                      BIGINT         NOT NULL COMMENT 'Maker用戶ID',
    taker_user_id                      BIGINT         NOT NULL COMMENT 'Taker用戶ID',
    order_id                           BIGINT         NOT NULL COMMENT 'Maker訂單ID (掛單方)',
    counterparty_order_id              BIGINT         NOT NULL COMMENT 'Taker訂單ID (吃單方)',
    order_quantity                     DECIMAL(20, 8) NOT NULL COMMENT 'Maker訂單總量',
    order_filled_quantity              DECIMAL(20, 8) NOT NULL COMMENT 'Maker訂單已成交量 (含當次成交)',
    counterparty_order_quantity        DECIMAL(20, 8) NOT NULL COMMENT 'Taker訂單總量',
    counterparty_order_filled_quantity DECIMAL(20, 8) NOT NULL COMMENT 'Taker訂單已成交量 (含當次成交)',
    order_side                         VARCHAR(10)    NOT NULL COMMENT 'Maker訂單方向: BUY, SELL',
    counterparty_order_side            VARCHAR(10)    NOT NULL COMMENT 'Taker訂單方向: BUY, SELL',
    maker_intent                       VARCHAR(20)    NOT NULL COMMENT 'Maker訂單意圖: INCREASE, REDUCE, CLOSE',
    taker_intent                       VARCHAR(20)    NOT NULL COMMENT 'Taker訂單意圖: INCREASE, REDUCE, CLOSE',


    # 成交
    trade_type                         VARCHAR(20)    NOT NULL DEFAULT 'NORMAL' COMMENT '成交類型: NORMAL, LIQUIDATION, ADL',
    price                              DECIMAL(20, 8) NOT NULL COMMENT '成交價格',
    quantity                           DECIMAL(20, 8) NOT NULL COMMENT '成交數量',
    total_value                        DECIMAL(20, 8) NOT NULL COMMENT '成交總價值 (price * quantity)',
    maker_fee                          DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT 'Maker手續費',
    taker_fee                          DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT 'Taker手續費',

    executed_at                        DATETIME       NOT NULL COMMENT '成交時間 (微秒精度)',
    created_at                         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '記錄創建時間',
    PRIMARY KEY (trade_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='成交紀錄表 - 撮合輸出供報表與事件回放';

CREATE INDEX `idx_instrument_executed` ON `trade` (`instrument_id`, `executed_at` DESC) COMMENT '查詢交易對歷史成交';
CREATE INDEX idx_order_id ON trade (order_id) COMMENT '查詢特定訂單所有成交';
CREATE INDEX idx_counterparty_order ON trade (counterparty_order_id) COMMENT '查詢對手訂單成交';
CREATE INDEX `idx_maker_user` ON `trade` (`maker_user_id`, `executed_at` DESC) COMMENT '查詢用戶作為Maker的成交';
CREATE INDEX `idx_taker_user` ON `trade` (`taker_user_id`, `executed_at` DESC) COMMENT '查詢用戶作為Taker的成交';
CREATE INDEX `idx_executed_at` ON `trade` (`executed_at` DESC) COMMENT '全市場成交時序查詢';

-- ==========================================
-- 5. Positions & Risk Tables
-- ==========================================

-- positions - 倉位主檔
CREATE TABLE positions
(
    position_id               BIGINT         NOT NULL COMMENT '倉位ID (Snowflake)',
    user_id                   BIGINT         NOT NULL COMMENT '用戶ID',
    instrument_id             BIGINT         NOT NULL COMMENT '交易對ID',

    # --- 核心配置 ---
    side                      VARCHAR(10)    NOT NULL COMMENT '倉位方向: LONG, SHORT',
    leverage                  INT            NOT NULL DEFAULT 4 COMMENT '當前槓桿倍數',
    status                    VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE' COMMENT '狀態: ACTIVE, LIQUIDATING, CLOSED',

    # --- 持倉狀態 (隨成交變動) ---
    quantity                  DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '持倉數量 (絕對值)',
    entry_price               DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '平均開倉價格',
    margin                    DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '當前倉位佔用保證金 (Isolated Margin)',
    closing_reserved_quantity DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '平倉凍結數量 (掛單中)',

    # --- 行情估值 (隨 Mark Price 變動) ---
    mark_price                DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '最新標記價格',
    unrealized_pnl            DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '未實現盈虧 (浮動盈虧)',
    margin_ratio              DECIMAL(10, 4) NOT NULL DEFAULT 0 COMMENT '保證金率 (Equity / Notional)',
    liquidation_price         DECIMAL(20, 8) NULL COMMENT '預估強平價格',

    # --- 累計績效 (用於報表與結算，解決 Event 聚合慢的問題) ---
    cum_realized_pnl          DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '累計已實現盈虧 (包含平倉損益)',
    cum_fee                   DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '累計交易手續費',
    cum_funding_fee           DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '累計資金費 (正=付, 負=收)',

    # --- 系統欄位 ---
    version                   INT            NOT NULL DEFAULT 0 COMMENT '樂觀鎖',
    created_at                DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    closed_at                 DATETIME       NULL COMMENT '完全平倉時間',

    PRIMARY KEY (position_id),
    UNIQUE KEY uk_user_instrument_active (user_id, instrument_id, (CASE
                                                                       WHEN status = 'ACTIVE' THEN 1
                                                                       ELSE NULL END)) COMMENT '單一用戶單一幣種僅存一筆ACTIVE倉位'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='倉位主檔 - 狀態快照與累計數據';

CREATE INDEX idx_user_status ON positions (user_id, status);
CREATE INDEX idx_margin_ratio ON positions (margin_ratio ASC) COMMENT '監控高風險倉位';

-- position_events - 倉位事件溯源表
CREATE TABLE position_events
(
    event_id        BIGINT      NOT NULL COMMENT '事件ID (Snowflake)',
    position_id     BIGINT      NOT NULL COMMENT '倉位ID',
    user_id         BIGINT      NOT NULL,
    instrument_id   BIGINT      NOT NULL,

    # --- 事件識別 ---
    # OPEN(開倉), INCREASE(加倉), DECREASE(減倉/平倉), CLOSE(全平)
    # LEVERAGE_ADJUST(調槓桿), MARGIN_ADJUST(調保證金)
    # FUNDING_FEE(資金費), LIQUIDATION(強平)
    event_type      VARCHAR(32) NOT NULL,
    sequence_number BIGINT      NOT NULL COMMENT '同一倉位內的事件序號 (確保順序性)',

    # --- 事件內容 (JSON，完整變動內容) ---
    payload         JSON        NOT NULL COMMENT '事件內容 (狀態變更的具體數據)',

    # --- 溯源關聯 ---
    reference_type  VARCHAR(32) NOT NULL COMMENT 'TRADE, ORDER, LIQUIDATION, SYSTEM',
    reference_id    VARCHAR(64) NOT NULL COMMENT '關聯單號 (TradeID:SIDE/OrderID)',

    occurred_at     DATETIME    NOT NULL COMMENT '業務發生時間',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (event_id),
    UNIQUE KEY uk_position_sequence (position_id, sequence_number) COMMENT '確保同一倉位事件順序唯一',
    INDEX `idx_position_occurred` (`position_id`, `occurred_at` DESC),
    INDEX idx_ref (reference_type, reference_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='倉位事件溯源表';

-- risk_limits - 風控參數表
CREATE TABLE risk_limits
(
    id                      BIGINT         NOT NULL COMMENT '記錄ID (Snowflake)',
    instrument_id           BIGINT         NOT NULL COMMENT '交易對ID (外鍵關聯 instruments.id)',

    # 開倉
    initial_margin_rate     DECIMAL(10, 4) NOT NULL COMMENT '初始保證金率 (開倉所需, 如0.01表示1%)',
    max_leverage            INT            NOT NULL COMMENT '最大槓桿倍數',

    # 強平
    maintenance_margin_rate DECIMAL(10, 4) NOT NULL COMMENT '維持保證金率 (低於此值觸發強平, 如0.005表示0.5%)',
    liquidation_fee_rate    DECIMAL(10, 4) NOT NULL DEFAULT 0.005 COMMENT '強平手續費率',

    # 倉位限制

    is_active               BOOLEAN        NOT NULL DEFAULT TRUE COMMENT '是否啟用',
    created_at              DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '創建時間',
    updated_at              DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新時間',
    PRIMARY KEY (id),
    UNIQUE KEY uk_instrument (instrument_id) COMMENT '同一交易對僅有一條規則'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='風控參數表 - 下單前保證金與限額判斷依據';

CREATE INDEX idx_instrument_active ON risk_limits (instrument_id, is_active) COMMENT '查詢交易對有效風控參數';

-- risk_snapshots - 保證金與風險指標快照
CREATE TABLE risk_snapshots
(
    id                      BIGINT          NOT NULL COMMENT '記錄ID (Snowflake)',
    user_id                 BIGINT          NOT NULL COMMENT '用戶ID',
    risk_version            BIGINT          NULL COMMENT '命中的風控規則版本 (risk_limits.id)',
    account_id              BIGINT          NULL COMMENT '帳戶ID (衍生交易帳本)',
    instrument_id           BIGINT          NOT NULL COMMENT '交易對ID',
    maintenance_margin_rate DECIMAL(10, 4)  NOT NULL COMMENT '維持保證金率快照',

    # position 事件更新
    position_id             BIGINT          NULL COMMENT '倉位ID (可選, 方便追蹤)',
    used_margin             DECIMAL(30, 12) NOT NULL COMMENT '實際佔用保證金',
    equity                  DECIMAL(30, 12) NOT NULL COMMENT 'equity = margin_balance + unrealized_pnl；margin_balance 等同該倉位自有保證金',
    notional_value          DECIMAL(30, 12) NOT NULL COMMENT 'market_notional = abs(mark_price * quantity * contract_size)',
    margin_ratio            DECIMAL(18, 8)  NOT NULL COMMENT '保證金率 (equity / market_notional)',
    liquidation_price       DECIMAL(30, 12) NULL COMMENT '預估強平價',


    status                  VARCHAR(32)     NOT NULL COMMENT 'NORMAL/ALERT/MARGIN_CALL/LIQUIDATION_PENDING',

    snapshot_source         VARCHAR(32)     NOT NULL DEFAULT 'POSITION_UPDATED' COMMENT '快照來源事件',
    snapshot_at             DATETIME        NOT NULL COMMENT '快照時間 (事件時間戳)',
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '創建時間',
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新時間',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_instrument (user_id, instrument_id) COMMENT '同一用戶+交易對僅保留最新紀錄'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='保證金與風險指標快照';

CREATE INDEX `idx_user_status` ON `risk_snapshots` (`user_id`, `status`, `snapshot_at` DESC) COMMENT '查詢用戶風險狀態';
CREATE INDEX `idx_instrument_snapshot` ON `risk_snapshots` (`instrument_id`, `snapshot_at` DESC) COMMENT '交易對風險監控';
CREATE INDEX `idx_snapshot_source` ON `risk_snapshots` (`snapshot_source`, `snapshot_at` DESC) COMMENT '依事件來源追蹤快照';

-- liquidation_queue - 強平佇列表
CREATE TABLE liquidation_queue
(
    id                BIGINT         NOT NULL COMMENT '強平任務ID (Snowflake)',
    position_id       BIGINT         NOT NULL COMMENT '倉位ID (外鍵關聯 positions.position_id)',
    user_id           BIGINT         NOT NULL COMMENT '用戶ID (冗餘欄位便於查詢)',
    instrument_id     BIGINT         NOT NULL COMMENT '交易對ID (冗餘欄位便於查詢)',

    # 強平狀態
    status            VARCHAR(20)    NOT NULL DEFAULT 'PENDING' COMMENT '狀態: PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED',
    liquidation_type  VARCHAR(20)    NOT NULL COMMENT '強平類型: MARGIN_CALL, FORCED_LIQUIDATION, ADL',
    priority          INT            NOT NULL DEFAULT 5 COMMENT '優先級 (1-10, 數字越小優先級越高)',
    queued_at         DATETIME       NOT NULL COMMENT '加入佇列時間',

    # 強平息息
    trigger_price     DECIMAL(20, 8) NOT NULL COMMENT '觸發強平時的標記價格',
    position_quantity DECIMAL(20, 8) NOT NULL COMMENT '待強平數量',
    margin_ratio      DECIMAL(10, 4) NOT NULL COMMENT '觸發時的保證金率',
    reason            VARCHAR(255)   NOT NULL COMMENT '強平原因',

    # 處理信息
    started_at        DATETIME       NULL COMMENT '開始處理時間',
    processed_at      DATETIME       NULL COMMENT '完成處理時間',
    liquidation_price DECIMAL(20, 8) NULL COMMENT '實際強平成交價格',
    liquidation_pnl   DECIMAL(20, 8) NULL COMMENT '強平盈虧',
    error_message     TEXT           NULL COMMENT '失敗原因',
    retry_count       INT            NOT NULL DEFAULT 0 COMMENT '重試次數',
    max_retries       INT            NOT NULL DEFAULT 3 COMMENT '最大重試次數',

    created_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '記錄創建時間',
    updated_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新時間',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='強平佇列表 - 追蹤強平排程與處理狀態';

CREATE INDEX idx_status_priority ON liquidation_queue (status, priority ASC, queued_at ASC) COMMENT '強平引擎按優先級處理';
CREATE INDEX idx_position ON liquidation_queue (position_id, status) COMMENT '查詢倉位強平狀態';
CREATE INDEX idx_user_status ON liquidation_queue (user_id, status) COMMENT '查詢用戶強平歷史';
CREATE INDEX `idx_instrument_queued` ON `liquidation_queue` (`instrument_id`, `queued_at` DESC) COMMENT '查詢交易對強平記錄';
CREATE INDEX `idx_queued_at` ON `liquidation_queue` (`queued_at` DESC) COMMENT '時序查詢強平事件';

-- ==========================================
-- 6. Market Data Tables
-- ==========================================

-- kline_buckets - K線聚合資料表
CREATE TABLE kline_buckets
(
    bucket_id          BIGINT          NOT NULL COMMENT 'K線記錄ID (Snowflake)',
    instrument_id      BIGINT          NOT NULL COMMENT '交易對ID',
    period VARCHAR (10) NOT NULL COMMENT '週期: 1m/5m/1h/1d ...',
    bucket_start       DATETIME        NOT NULL COMMENT 'K線時間窗口起始 (含)',
    bucket_end         DATETIME        NOT NULL COMMENT 'K線時間窗口結束 (不含)',

    open_price         DECIMAL(20, 8)  NOT NULL COMMENT '開盤價',
    high_price         DECIMAL(20, 8)  NOT NULL COMMENT '最高價',
    low_price          DECIMAL(20, 8)  NOT NULL COMMENT '最低價',
    close_price        DECIMAL(20, 8)  NOT NULL COMMENT '收盤價',
    volume             DECIMAL(30, 12) NOT NULL DEFAULT 0 COMMENT '成交量 (以 base asset 計)',
    turnover           DECIMAL(30, 12) NOT NULL DEFAULT 0 COMMENT '成交額 (以 quote asset 計)',
    trade_count        INT             NOT NULL DEFAULT 0 COMMENT '成交筆數',
    taker_buy_volume   DECIMAL(30, 12) NULL COMMENT '吃單方成交量 (多頭)',
    taker_buy_turnover DECIMAL(30, 12) NULL COMMENT '吃單方成交額 (多頭)',

    is_closed          BOOLEAN         NOT NULL DEFAULT TRUE COMMENT '是否完整收斂 (避免延遲成交補寫)',
    created_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '創建時間',
    updated_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新時間',
    PRIMARY KEY (bucket_id),
    UNIQUE KEY uk_instrument_period_start (instrument_id, period, bucket_start) COMMENT '單商品+週期+起始時間唯一'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='K線聚合資料表 - 供 REST 查詢的歷史 OHLC';

CREATE INDEX `idx_instrument_period` ON `kline_buckets` (`instrument_id`, `period`, `bucket_start` DESC) COMMENT '查詢交易對指定週期歷史K線';
CREATE INDEX `idx_period_time` ON `kline_buckets` (`period`, `bucket_start` DESC) COMMENT '按週期倒序遍歷';

-- mark_price_snapshots - 標記價快照表
CREATE TABLE mark_price_snapshots
(
    snapshot_id            BIGINT         NOT NULL COMMENT '快照ID (Snowflake)',
    instrument_id          BIGINT         NOT NULL COMMENT '交易對ID',
    mark_price             DECIMAL(20, 8) NOT NULL COMMENT '標記價 = 最新成交價',
    mark_price_change_rate DECIMAL(20, 8) NOT NULL COMMENT '標記價變化比率 = 新/舊',
    trade_id               BIGINT         NOT NULL COMMENT '來源交易 ID',
    trade_executed_at      DATETIME       NOT NULL COMMENT '成交時間',
    calculated_at          DATETIME       NOT NULL COMMENT '寫入/計算時間',
    created_at             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建立時間',
    updated_at             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新時間',
    PRIMARY KEY (snapshot_id),
    UNIQUE KEY uk_instrument_calculated (instrument_id, calculated_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='標記價快照表 (以最新成交價為基準)';

CREATE INDEX `idx_mark_price_snapshot` ON `mark_price_snapshots` (`instrument_id`, `calculated_at` DESC);

-- ==========================================
-- 7. System & Messaging Tables
-- ==========================================

-- retry_task - 通用待處理任務表
CREATE TABLE retry_task
(
    -- 1. 唯一標識與索引
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主鍵 ID',
    biz_type      VARCHAR(50)     NOT NULL COMMENT '業務類型 (如: ORDER_SYNC, EMAIL_SEND)',
    biz_key       VARCHAR(100)    NOT NULL COMMENT '業務唯一鍵 (如: 訂單號, 用戶ID)，用於冪等檢查',

    -- 2. 核心狀態控制
    status        VARCHAR(32)     NOT NULL DEFAULT 'PENDING' COMMENT '狀態: PENDING, PROCESSING, SUCCESS, FAIL_RETRY, FAIL_TERMINAL',
    priority      TINYINT         NOT NULL DEFAULT 10 COMMENT '優先級: 數值越小優先級越高',

    -- 3. 業務數據 (核心通用部分)
    payload       JSON COMMENT '業務數據內容 (JSON 格式)，儲存執行任務所需的所有參數',
    result_msg    TEXT COMMENT '執行結果或錯誤訊息記錄',

    -- 4. 重試與排程機制
    retry_count   INT             NOT NULL DEFAULT 0 COMMENT '已重試次數',
    max_retries   INT             NOT NULL DEFAULT 3 COMMENT '最大重試次數',
    next_run_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下一次執行時間 (用於延遲執行或退避策略)',

    -- 5. 並發控制與審計
    version       INT             NOT NULL DEFAULT 0 COMMENT '樂觀鎖版本號 (防止多個 Worker 同時搶同一個任務)',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '創建時間',
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新時間',

    PRIMARY KEY (id),
    -- 索引設計建議
    UNIQUE KEY uk_biz (biz_type, biz_key),           -- 防止重複提交相同任務
    INDEX idx_scan (status, next_run_time, priority) -- 用於 Worker 掃描任務
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='通用待處理任務表';

-- mq_outbox - 事件外送 Outbox 表
CREATE TABLE mq_outbox
(
    event_id       VARCHAR(36)  NOT NULL COMMENT 'UUID 去重鍵',
    aggregate_type VARCHAR(100) NOT NULL COMMENT '原始業務事件的類型，目標Topic 由 aggregate_type → 規則路由決定',
    aggregate_id   BIGINT       NOT NULL COMMENT '原始業務事件的ID',
    event_type     VARCHAR(64)  NOT NULL COMMENT '事件類型',
    payload        JSON         NOT NULL COMMENT '事件內容',
    headers        JSON         NULL COMMENT '可選標頭 trace等',
    seq            BIGINT       NULL COMMENT '業務序號 保序/重放基準',
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '生成時間',
    PRIMARY KEY (event_id),
    UNIQUE KEY uk_event_type_seq (event_type, seq),
    KEY idx_created (created_at),
    KEY idx_agg (aggregate_type, aggregate_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- mq_consumer_offsets - 消費者組偏移量表
CREATE TABLE mq_consumer_offsets
(
    consumer_group VARCHAR(100) NOT NULL COMMENT '消費者群組',
    topic          VARCHAR(100) NOT NULL COMMENT '來源 Topic',
    partition_id   INT          NOT NULL COMMENT '分片編號',
    last_seq       BIGINT       NOT NULL COMMENT '最後處理的序號',
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新時間',
    PRIMARY KEY (consumer_group, topic, partition_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- mq_dead_letters - 死信表
CREATE TABLE mq_dead_letters
(
    id         BIGINT      NOT NULL PRIMARY KEY COMMENT '死信 ID Snowflake',
    source     VARCHAR(64) NOT NULL COMMENT '來源 例如 outbox matching-engine',
    seq        BIGINT      NULL COMMENT '業務序號 可為 NULL',
    outbox_id  BIGINT      NULL COMMENT '對應 Outbox ID',
    payload    JSON        NOT NULL COMMENT '事件資料內容',
    error      TEXT        NOT NULL COMMENT '錯誤詳細內容',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '記錄時間',
    KEY idx_source_time (source, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- ==========================================
-- 8. Data Generation
-- ==========================================

-- INSERT Sample Data for Instruments (Perpetual Contract)
INSERT INTO instrument (instrument_id, symbol, name, base_asset, quote_asset, instrument_type, status,
                        maker_fee_rate, taker_fee_rate, contract_size, default_leverage,
                        launch_at, delist_at, display_order, is_tradable, is_visible, description, metadata,
                        created_at, updated_at)
VALUES (10001, 'BTCUSDT-PERP', 'BTCUSDT', 'BTC', 'USDT', 'PERPETUAL', 'ACTIVE',
        0.0002, 0.0005, 0.001, 4,
        NOW(), NULL, 1, TRUE, TRUE, 'Demo perpetual pair', '{
    "funding_interval": 8
  }',
        NOW(), NOW());

-- INSERT Sample Risk Limit for BTCUSDT-PERP
-- 初始保證金 0.3 (4x 槓桿), 維持保證金 0.25
INSERT INTO risk_limits (id, instrument_id, initial_margin_rate, max_leverage, maintenance_margin_rate,
                         liquidation_fee_rate, is_active, created_at, updated_at)
VALUES (1, 10001, 0.3000, 4, 0.25, 0.005, TRUE, NOW(), NOW());

INSERT INTO users (id, external_id, email, status, created_at, updated_at)
VALUES (761365317218437, 'user-1768016605751', 'c.p.kevinf13@gmail.com', 'ACTIVE', '2026-01-10 03:43:26',
        '2026-01-10 03:43:26');
INSERT INTO users (id, external_id, email, status, created_at, updated_at)
VALUES (761365370314885, 'user-1768016619212', 'c.p.kevinf13-2@gmail.com', 'ACTIVE', '2026-01-10 03:43:40',
        '2026-01-10 03:43:40');
INSERT INTO users (id, external_id, email, status, created_at, updated_at)
VALUES (762050502422661, 'user-1768183887232', 'c.p.kevinf13-3@gmail.com', 'ACTIVE', '2026-01-12 02:11:27',
        '2026-01-12 02:11:27');

INSERT INTO auth_credentials (id, user_id, credential_type, secret_hash, salt, status, expires_at, created_at)
VALUES (761365318348869, 761365317218437, 'PASSWORD', '$2a$10$6/PeRu7T7TH8tIW4h2HF3uyYTslO4Xkl8EQwwXM9fP7mz0J.3IkMG',
        'cdd898ba-54fd-44f4-a33d-ba0f4f768edf', 'ACTIVE', null, '2026-01-10 03:43:26');
INSERT INTO auth_credentials (id, user_id, credential_type, secret_hash, salt, status, expires_at, created_at)
VALUES (761365370511429, 761365370314885, 'PASSWORD', '$2a$10$rLSzRXBmBTRGf.P7EW5k4OlZcLrss72lRwHNtYtOiMwJihJIdhX/u',
        'dc6789c5-e451-47e8-ba50-c046954b87cd', 'ACTIVE', null, '2026-01-10 03:43:40');
INSERT INTO auth_credentials (id, user_id, credential_type, secret_hash, salt, status, expires_at, created_at)
VALUES (762050502996037, 762050502422661, 'PASSWORD', '$2a$10$wPrxh2LfBy3ysymtrVS35ud1Z9JYW0ukQOy/NkQQCTVj6oX2CkFS2',
        '90e0f1fa-1db3-4746-88b6-5a95331a45a4', 'ACTIVE', null, '2026-01-12 02:11:28');
