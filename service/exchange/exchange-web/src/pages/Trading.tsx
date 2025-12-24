import { useState } from 'react';
import { InputNumber, message } from 'antd';
import './Trading.css';

export default function Trading() {
  const [orderType, setOrderType] = useState<'LIMIT' | 'MARKET'>('LIMIT');
  const [side, setSide] = useState<'BUY' | 'SELL'>('BUY');
  const [price, setPrice] = useState<number>(0);
  const [quantity, setQuantity] = useState<number>(0);
  const [loading, setLoading] = useState(false);
  const lastPrice = 67243.15;
  const highPrice = 67880.2;
  const lowPrice = 66210.4;
  const changePct = 2.18;
  const estimatedPrice = orderType === 'MARKET' ? lastPrice : (price || lastPrice);
  const estimatedCost = quantity ? estimatedPrice * quantity : 0;

  const handleSubmit = async (targetSide: 'BUY' | 'SELL') => {
    if (side !== targetSide) {
      setSide(targetSide);
    }
    if (orderType === 'LIMIT' && !price) {
      message.error('請輸入價格');
      return;
    }
    if (!quantity) {
      message.error('請輸入數量');
      return;
    }

    setLoading(true);
    try {
      // TODO: 調用下單 API
      // await submitOrder({ type: orderType, side, price, quantity });

      message.success('下單成功！');
    } catch (error) {
      message.error('下單失敗');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="trading-page">
      <div className="trading-shell">
        <section className="glass-card chart-card panel-rise delay-1">
          <div className="chart-header">
            <div className="brand-block">
              <span className="brand-mark" />
              <span className="brand-title">Liquid Flow</span>
              <div className="pair-block">
                <span className="pair-pill">BTC/USDT</span>
                <span className="pair-pill">15m</span>
              </div>
            </div>
            <div className="price-block">
              <div className="price-main">${lastPrice.toLocaleString()}</div>
              <div className="price-sub">今日 {changePct}%</div>
            </div>
          </div>
          <div className="chart-body">
            <div className="chart-area">
              <div className="chart-grid" />
              <svg className="kline-svg" viewBox="0 0 800 240" preserveAspectRatio="none">
                <defs>
                  <linearGradient id="kline-fill" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#4b87c5" stopOpacity="0.35" />
                    <stop offset="100%" stopColor="#ffffff" stopOpacity="0.05" />
                  </linearGradient>
                </defs>
                <path
                  d="M20 180 L70 150 L110 170 L150 120 L190 130 L230 110 L270 125 L310 90 L350 105 L390 80 L430 95 L470 70 L510 85 L550 60 L590 72 L630 58 L670 75 L710 50 L760 62"
                  fill="none"
                  stroke="#2d5f9c"
                  strokeWidth="2.2"
                />
                <path
                  d="M20 180 L70 150 L110 170 L150 120 L190 130 L230 110 L270 125 L310 90 L350 105 L390 80 L430 95 L470 70 L510 85 L550 60 L590 72 L630 58 L670 75 L710 50 L760 62 L760 220 L20 220 Z"
                  fill="url(#kline-fill)"
                />
                <g stroke="#2d5f9c" strokeWidth="2">
                  <line x1="60" y1="160" x2="60" y2="195" />
                  <line x1="120" y1="140" x2="120" y2="185" />
                  <line x1="180" y1="150" x2="180" y2="205" />
                  <line x1="240" y1="120" x2="240" y2="170" />
                  <line x1="300" y1="110" x2="300" y2="165" />
                  <line x1="360" y1="95" x2="360" y2="150" />
                  <line x1="420" y1="80" x2="420" y2="130" />
                  <line x1="480" y1="70" x2="480" y2="120" />
                  <line x1="540" y1="60" x2="540" y2="115" />
                  <line x1="600" y1="55" x2="600" y2="110" />
                  <line x1="660" y1="65" x2="660" y2="120" />
                  <line x1="720" y1="50" x2="720" y2="100" />
                </g>
                <g fill="#e5eff8" stroke="#2d5f9c" strokeWidth="1.5">
                  <rect x="52" y="170" width="16" height="20" rx="3" />
                  <rect x="112" y="155" width="16" height="20" rx="3" />
                  <rect x="172" y="168" width="16" height="22" rx="3" />
                  <rect x="232" y="140" width="16" height="18" rx="3" />
                  <rect x="292" y="135" width="16" height="18" rx="3" />
                  <rect x="352" y="110" width="16" height="20" rx="3" />
                  <rect x="412" y="95" width="16" height="18" rx="3" />
                  <rect x="472" y="90" width="16" height="18" rx="3" />
                  <rect x="532" y="80" width="16" height="18" rx="3" />
                  <rect x="592" y="75" width="16" height="18" rx="3" />
                  <rect x="652" y="85" width="16" height="18" rx="3" />
                  <rect x="712" y="70" width="16" height="18" rx="3" />
                </g>
              </svg>
            </div>
            <div className="chart-stats">
              <div className="stat-item">
                <div className="stat-label">最新價</div>
                <div className="stat-value">${lastPrice.toLocaleString()}</div>
              </div>
              <div className="stat-item">
                <div className="stat-label">24h 最高</div>
                <div className="stat-value">${highPrice.toLocaleString()}</div>
              </div>
              <div className="stat-item">
                <div className="stat-label">24h 最低</div>
                <div className="stat-value">${lowPrice.toLocaleString()}</div>
              </div>
              <div className="stat-item">
                <div className="stat-label">成交量</div>
                <div className="stat-value">1,248 BTC</div>
              </div>
            </div>
          </div>
        </section>

        <section className="info-grid">
          <div className="glass-card info-card panel-rise delay-2">
            <div className="card-title">Order Book</div>
            <div className="orderbook">
              <ul>
                <li><span>67,248.5</span><span>0.038</span></li>
                <li><span>67,244.0</span><span>0.052</span></li>
                <li><span>67,240.8</span><span>0.041</span></li>
                <li><span>67,238.2</span><span>0.063</span></li>
              </ul>
              <ul>
                <li><span>67,230.1</span><span>0.028</span></li>
                <li><span>67,225.7</span><span>0.044</span></li>
                <li><span>67,221.9</span><span>0.036</span></li>
                <li><span>67,219.3</span><span>0.051</span></li>
              </ul>
            </div>
          </div>
          <div className="glass-card info-card panel-rise delay-3">
            <div className="card-title">Position</div>
            <div className="position-metrics">
              <div>Available: <strong>0.3200 BTC</strong></div>
              <div>Margin: <strong>0.0092 BTC</strong></div>
              <div>Leverage: <strong>20x</strong></div>
              <div>Liq. Price: <strong>$57,450.00</strong></div>
              <div>Unrealized PnL: <strong>+$1,280.50</strong></div>
            </div>
          </div>
          <div className="glass-card info-card panel-rise delay-4">
            <div className="card-title">Recent Trades</div>
            <div className="trade-stream">
              <div className="trade-row"><span>15:32:21</span><span>0.012 BTC</span></div>
              <div className="trade-row"><span>15:31:58</span><span>0.008 BTC</span></div>
              <div className="trade-row"><span>15:31:40</span><span>0.015 BTC</span></div>
              <div className="trade-row"><span>15:31:05</span><span>0.020 BTC</span></div>
            </div>
          </div>
        </section>

        <section className="glass-card order-card panel-rise delay-2">
          <div className="order-header">
            <div className="card-title">下單</div>
            <div className="segmented">
              <button
                className={`segment-button ${orderType === 'LIMIT' ? 'active' : ''}`}
                onClick={() => setOrderType('LIMIT')}
                type="button"
              >
                限價
              </button>
              <button
                className={`segment-button ${orderType === 'MARKET' ? 'active' : ''}`}
                onClick={() => setOrderType('MARKET')}
                type="button"
              >
                市價
              </button>
            </div>
          </div>

          <div className="order-header">
            <div className="card-title">方向</div>
            <div className="segmented">
              <button
                className={`segment-button buy ${side === 'BUY' ? 'active' : ''}`}
                onClick={() => setSide('BUY')}
                type="button"
              >
                買入
              </button>
              <button
                className={`segment-button sell ${side === 'SELL' ? 'active' : ''}`}
                onClick={() => setSide('SELL')}
                type="button"
              >
                賣出
              </button>
            </div>
          </div>

          <div className="order-grid">
            {orderType === 'LIMIT' && (
              <div className="field-group">
                <div className="field-label">價格</div>
                <InputNumber
                  style={{ width: '100%' }}
                  placeholder="輸入價格"
                  value={price}
                  onChange={(val) => setPrice(val || 0)}
                  min={0}
                />
              </div>
            )}
            <div className="field-group">
              <div className="field-label">數量</div>
              <InputNumber
                style={{ width: '100%' }}
                placeholder="輸入數量"
                value={quantity}
                onChange={(val) => setQuantity(val || 0)}
                min={0}
              />
            </div>
            <div className="order-summary">
              <div className="summary-row">
                <span>估算價格</span>
                <span>${estimatedPrice.toLocaleString()}</span>
              </div>
              <div className="summary-row">
                <span>手續費</span>
                <span>0.03%</span>
              </div>
              <div className="summary-row">
                <span>預估成本</span>
                <span>${estimatedCost.toLocaleString(undefined, { maximumFractionDigits: 2 })}</span>
              </div>
            </div>
          </div>

          <div className="order-actions">
            <button
              className="action-button action-buy"
              onClick={() => handleSubmit('BUY')}
              type="button"
              disabled={loading}
            >
              {loading ? '送出中...' : 'Buy / Long BTC'}
            </button>
            <button
              className="action-button action-sell"
              onClick={() => handleSubmit('SELL')}
              type="button"
              disabled={loading}
            >
              {loading ? '送出中...' : 'Sell / Short BTC'}
            </button>
          </div>
        </section>
      </div>
    </div>
  );
}
