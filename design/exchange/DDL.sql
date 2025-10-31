-- users：核心使用者主檔，供登入、下單與帳務引用
  CREATE TABLE `users` (
    `id`            BIGINT        NOT NULL COMMENT '平台內部使用者主鍵',
    `external_id`   VARCHAR(64)   DEFAULT NULL COMMENT '外部系統引用用戶 ID（可選）',
    `email`         VARCHAR(255)  NOT NULL COMMENT '登入/通知用 Email，唯一',
    `status`        VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE' COMMENT '使用者狀態（ACTIVE / LOCKED / DISABLED）',
    `created_at`    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建立時間',
    `updated_at`    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最後更新時間',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_email` (`email`),
    UNIQUE KEY `uk_users_external` (`external_id`)
  ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '使用者主檔';

  -- pending_auth_credentials：使用者註冊時暫存 auth 雜湊與重試狀態
  --  - user 服務會先呼叫 auth 的 /prepare 取得 hash/salt，再寫入此表
  --  - 首次寫入 auth 成功即標記 COMPLETED；失敗則保留為 PENDING 供排程重試
  --  - retry_count、next_retry_at、last_error 讓補償流程掌握重試節奏與失敗原因
  CREATE TABLE pending_auth_credentials (
      id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
      user_id           BIGINT UNSIGNED NOT NULL,
      credential_type   VARCHAR(32)     NOT NULL COMMENT '憑證型別（PASSWORD / API_KEY ...）',
      secret_hash       VARCHAR(512)    NOT NULL COMMENT '已經由 auth 算好的 hash',
      salt              VARCHAR(128)    NOT NULL,
      status            VARCHAR(32)     NOT NULL COMMENT 'PENDING / COMPLETED / FAILED',
      retry_count       INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '已重試次數',
      next_retry_at     DATETIME        NULL COMMENT '下次排程可撿起的時間',
      last_error        VARCHAR(512)    NULL COMMENT '最近一次失敗訊息',
      created_at        DATETIME        NOT NULL,
      updated_at        DATETIME        NOT NULL,
      UNIQUE KEY uk_user_type (user_id, credential_type)
  ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

  -- auth_credentials：儲存登入憑證雜湊與型別
  CREATE TABLE `auth_credentials` (
    `id`              BIGINT       NOT NULL COMMENT '憑證主鍵',
    `user_id`         BIGINT       NOT NULL COMMENT '對應使用者 ID',
    `credential_type` VARCHAR(32)  NOT NULL COMMENT '憑證型別（PASSWORD / API_KEY / FIDO 等）',
    `secret_hash`     VARCHAR(255) NOT NULL COMMENT '憑證雜湊值',
    `salt`            VARCHAR(255) NOT NULL COMMENT '雜湊鹽值',
    `status`          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '憑證狀態',
    `expires_at`      TIMESTAMP    NULL COMMENT '可選的到期時間',
    `created_at`      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建立時間',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_auth_credentials_user_type` (`user_id`, `credential_type`),
    CONSTRAINT `fk_auth_credentials_user`
      FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
        ON UPDATE CASCADE ON DELETE CASCADE
  ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '登入憑證資料表';

  -- orders：委託主檔，記錄下單請求
  CREATE TABLE `orders` (
    `order_id`         BIGINT        NOT NULL COMMENT '委託單 ID',
    `user_id`          BIGINT        NOT NULL COMMENT '下單使用者 ID',
    `instrument_id`    VARCHAR(64)   NOT NULL COMMENT '交易標的 ID / 交易對',
    `client_order_id`  VARCHAR(64)   DEFAULT NULL COMMENT '客戶自訂委託 ID，用於冪等',
    `side`             VARCHAR(8)    NOT NULL COMMENT '買賣方向（BUY / SELL）',
    `type`             VARCHAR(16)   NOT NULL COMMENT '委託型別（MARKET / LIMIT / ...）',
    `price`            DECIMAL(30,10) DEFAULT NULL COMMENT '限價單價格（市價單可為 NULL）',
    `quantity`         DECIMAL(30,10) NOT NULL COMMENT '委託數量',
    `status`           VARCHAR(16)   NOT NULL COMMENT '委託狀態（NEW / FILLED / CANCELLED 等）',
    `time_in_force`    VARCHAR(16)   DEFAULT NULL COMMENT '委託有效期（GTC / IOC / FOK 等）',
    `created_at`       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '委託建立時間',
    `updated_at`       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最後狀態異動時間',
    PRIMARY KEY (`order_id`),
    UNIQUE KEY `uk_orders_client` (`user_id`, `client_order_id`),
    KEY `idx_orders_user` (`user_id`),
    KEY `idx_orders_instrument_status` (`instrument_id`, `status`),
    CONSTRAINT `fk_orders_user`
      FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
        ON UPDATE CASCADE ON DELETE CASCADE
  ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '委託主檔';

  -- trade_tickers：撮合產生的成交紀錄
  CREATE TABLE `trade_tickers` (
    `trade_id`            BIGINT        NOT NULL COMMENT '成交紀錄 ID',
    `order_id`            BIGINT        NOT NULL COMMENT '本方委託 ID',
    `opposite_order_id`   BIGINT        DEFAULT NULL COMMENT '對手方委託 ID（可為 NULL）',
    `instrument_id`       VARCHAR(64)   NOT NULL COMMENT '交易標的 ID',
    `price`               DECIMAL(30,10) NOT NULL COMMENT '成交價格',
    `quantity`            DECIMAL(30,10) NOT NULL COMMENT '成交數量',
    `fee`                 DECIMAL(30,10) NOT NULL DEFAULT 0 COMMENT '手續費（正為收費，負為返佣）',
    `executed_at`         TIMESTAMP     NOT NULL COMMENT '成交時間',
    PRIMARY KEY (`trade_id`),
    KEY `idx_trade_tickers_order` (`order_id`),
    KEY `idx_trade_tickers_instrument_time` (`instrument_id`, `executed_at`),
    CONSTRAINT `fk_trade_tickers_order`
      FOREIGN KEY (`order_id`) REFERENCES `orders` (`order_id`)
        ON UPDATE CASCADE ON DELETE CASCADE
  ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '撮合成交紀錄';

  -- ledger_entries：雙分錄資產明細
  CREATE TABLE `ledger_entries` (
    `entry_id`        BIGINT        NOT NULL COMMENT '資產變動紀錄 ID',
    `account_id`      BIGINT        NOT NULL COMMENT '帳戶 ID（子帳戶或資產帳戶）',
    `user_id`         BIGINT        NOT NULL COMMENT '對應使用者 ID',
    `asset`           VARCHAR(32)   NOT NULL COMMENT '資產代碼（USDT、BTC 等）',
    `amount`          DECIMAL(30,10) NOT NULL COMMENT '變動金額（正為入帳、負為出帳）',
    `direction`       VARCHAR(6)    NOT NULL COMMENT '借貸方向（DEBIT / CREDIT）',
    `reference_type`  VARCHAR(32)   NOT NULL COMMENT '參考類型（TRADE / FUNDING / FEE 等）',
    `reference_id`    VARCHAR(64)   NOT NULL COMMENT '參考主鍵（例如成交 ID）',
    `event_time`      TIMESTAMP     NOT NULL COMMENT '事件發生時間',
    `created_at`      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '記帳時間',
    PRIMARY KEY (`entry_id`),
    KEY `idx_ledger_entries_account_asset` (`account_id`, `asset`),
    KEY `idx_ledger_entries_reference` (`reference_type`, `reference_id`),
    CONSTRAINT `fk_ledger_entries_user`
      FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
        ON UPDATE CASCADE ON DELETE CASCADE
  ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '雙分錄資產變動表';

  -- positions：倉位快照
  CREATE TABLE `positions` (
    `position_id`        BIGINT        NOT NULL COMMENT '倉位 ID',
    `user_id`            BIGINT        NOT NULL COMMENT '使用者 ID',
    `instrument_id`      VARCHAR(64)   NOT NULL COMMENT '交易標的 ID',
    `side`               VARCHAR(8)    NOT NULL COMMENT '倉位方向（LONG / SHORT）',
    `quantity`           DECIMAL(30,10) NOT NULL COMMENT '倉位數量',
    `entry_price`        DECIMAL(30,10) NOT NULL COMMENT '建倉均價',
    `mark_price`         DECIMAL(30,10) NOT NULL COMMENT '最新標記價格',
    `unrealized_pnl`     DECIMAL(30,10) NOT NULL DEFAULT 0 COMMENT '未實現損益',
    `liquidation_price`  DECIMAL(30,10) DEFAULT NULL COMMENT '預估強平價（可選）',
    `updated_at`         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最後更新時間',
    PRIMARY KEY (`position_id`),
    UNIQUE KEY `uk_positions_user_instrument_side` (`user_id`, `instrument_id`, `side`),
    CONSTRAINT `fk_positions_user`
      FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
        ON UPDATE CASCADE ON DELETE CASCADE
  ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '倉位快照';
