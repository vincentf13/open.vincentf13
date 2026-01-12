import { useState, useEffect } from 'react';
import { Tooltip } from 'antd';
import { createOrder, type OrderSide, type OrderType } from '../../api/order';
import type { InstrumentSummary } from '../../api/instrument';
import { getRiskLimit, type RiskLimitResponse } from '../../api/risk';

type TradeFormProps = {
  instrument: InstrumentSummary | null;
  onOrderCreated?: () => void;
  refreshTrigger?: number;
  isPaused?: boolean;
};

export default function TradeForm({ instrument, onOrderCreated, refreshTrigger, isPaused }: TradeFormProps) {
  const [side, setSide] = useState<OrderSide>('BUY');
  const [type, setType] = useState<OrderType>('LIMIT');
  const [price, setPrice] = useState<string>('');
  const [quantity, setQuantity] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [riskLimit, setRiskLimit] = useState<RiskLimitResponse | null>(null);
  const buildClientOrderId = () => {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }
    return `${Date.now()}-${Math.random().toString(16).slice(2, 10)}`;
  };

  // Reset form when instrument changes
  useEffect(() => {
    setPrice('');
    setQuantity('');
  }, [instrument?.instrumentId]);
  useEffect(() => {
    let cancelled = false;
    if (!instrument?.instrumentId) {
      setRiskLimit(null);
      return () => {
        cancelled = true;
      };
    }
    const fetchRiskLimit = () => {
      getRiskLimit(instrument.instrumentId)
        .then((response) => {
          if (cancelled) {
            return;
          }
          if (String(response?.code) === '0') {
            setRiskLimit(response?.data ?? null);
          } else if (!riskLimit) {
            setRiskLimit(null);
          }
        })
        .catch(() => {
          if (!cancelled && !riskLimit) {
            setRiskLimit(null);
          }
        });
    };
    fetchRiskLimit();
    return () => {
      cancelled = true;
    };
  }, [instrument?.instrumentId, isPaused, refreshTrigger]);

  const handleSubmit = async () => {
    if (!instrument?.instrumentId) {
      alert('Please select an instrument first.');
      return;
    }
    
    if (!quantity || isNaN(Number(quantity)) || Number(quantity) <= 0) {
      alert('Please enter a valid quantity.');
      return;
    }

    if (type === 'LIMIT' && (!price || isNaN(Number(price)) || Number(price) <= 0)) {
        alert('Please enter a valid price for Limit order.');
        return;
    }

    setLoading(true);
    try {
      const clientOrderId = buildClientOrderId();
      const result = await createOrder({
        instrumentId: Number(instrument.instrumentId),
        side,
        type,
        quantity: normalizedQuantity,
        price: type === 'LIMIT' ? Number(price) : null,
        clientOrderId,
      });
      if (String(result?.code) !== '0') {
        alert(`Error: ${result?.message || 'Failed to place order'}`);
        return;
      }
      onOrderCreated?.();
      alert('Order placed successfully!');
    } catch (error: any) {
      console.error('Order creation failed:', error);
      const msg = error.response?.data?.message || 'Failed to place order';
      alert(`Error: ${msg}`);
    } finally {
      setLoading(false);
    }
  };

  const contractSizeValue = Number(instrument?.contractSize);
  const contractMultiplier = Number.isFinite(contractSizeValue) && contractSizeValue > 0 ? contractSizeValue : 1;
  const parsedQuantity = Number(quantity);
  const normalizedQuantity = Number.isFinite(parsedQuantity) ? parsedQuantity / contractMultiplier : NaN;
  const displayNormalizedQuantity = Number.isFinite(normalizedQuantity)
    ? Number(normalizedQuantity.toFixed(6))
    : '--';
  const baseTotal = (Number(price) || 0) * (Number(quantity) || 0);
  const takerFeeRateValue = Number(instrument?.takerFeeRate);
  const takerFeeRate = Number.isFinite(takerFeeRateValue) ? takerFeeRateValue : 0;
  const estimatedFee = baseTotal * takerFeeRate;
  const initialMarginRateValue = Number(riskLimit?.initialMarginRate);
  const initialMarginRate = Number.isFinite(initialMarginRateValue) ? initialMarginRateValue : 1;
  const total = baseTotal * initialMarginRate + estimatedFee;
  const defaultLeverageValue = Number(instrument?.defaultLeverage);
  const displayLeverage = Number.isFinite(defaultLeverageValue) ? defaultLeverageValue : null;
  const formatRate = (value?: number | string | null) => {
    if (value === null || value === undefined || value === '') {
      return '--';
    }
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) {
      return String(value);
    }
    return `${(numeric * 100).toFixed(2)}%`;
  };

  return (
    <div className="flex flex-col h-full">
      {/* Side Selector (Buy/Sell) */}
      <div className="flex items-center gap-2 p-3 border-b border-white/20">
        <button
          onClick={() => setSide('BUY')}
          className={`flex-1 py-2 rounded-xl text-sm font-semibold transition-all ${
            side === 'BUY' 
              ? 'bg-emerald-500 text-white shadow-lg shadow-emerald-500/20' 
              : 'bg-white/30 text-slate-600 hover:bg-white/50'
          }`}
        >
          Buy
        </button>
        <button
          onClick={() => setSide('SELL')}
          className={`flex-1 py-2 rounded-xl text-sm font-semibold transition-all ${
            side === 'SELL' 
              ? 'bg-rose-500 text-white shadow-lg shadow-rose-500/20' 
              : 'bg-white/30 text-slate-600 hover:bg-white/50'
          }`}
        >
          Sell
        </button>
      </div>

      <div className="p-4 space-y-4">
        {/* Type Selector (Limit Only) */}
        <div className="space-y-1.5">
            <label className="text-xs font-medium text-slate-500 uppercase tracking-wider">Type</label>
            <div className="grid grid-cols-1 gap-1 bg-white/20 p-1 rounded-xl border border-white/30">
                <div 
                  className="text-xs py-1.5 rounded-lg bg-white shadow-sm text-slate-800 font-semibold text-center cursor-default"
                >
                    Limit
                </div>
            </div>
        </div>

        {/* Price Input */}
        <div className="space-y-1.5">
            <label className="text-[10px] font-bold text-slate-400 uppercase tracking-widest pl-1">Price</label>
            <div className={`liquid-input-group ${type === 'MARKET' ? 'bg-slate-100 cursor-not-allowed border-slate-200' : 'border-slate-300'}`}>
                <input 
                  type="number" 
                  step="0.01"
                  value={type === 'MARKET' ? '' : price}
                  onChange={(e) => setPrice(e.target.value)}
                  disabled={type === 'MARKET'}
                  placeholder={type === 'MARKET' ? 'Market Price' : '0.00'}
                  className={`flex-1 bg-transparent border-none outline-none py-1.5 pl-3 pr-2 text-right font-mono text-sm text-slate-700 min-w-0 ${
                    type === 'MARKET' ? 'placeholder-slate-400 cursor-not-allowed' : ''
                  }`} 
                />
                <div className="flex items-center justify-end w-[60px] pr-3 border-l border-slate-200 bg-slate-50/50">
                  <span className="text-xs text-slate-500 font-bold whitespace-nowrap select-none text-right">
                    {instrument?.quoteAsset || ''}
                  </span>
                </div>
            </div>
        </div>

        {/* Quantity Input */}
        <div className="space-y-1.5">
            <div className="flex items-center gap-1 pl-1">
              <label className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Amount</label>
              <Tooltip
                title={(
                  <div className="text-xs">
                    <div className="whitespace-nowrap font-bold mb-1">下單數量說明</div>
                    <div className="whitespace-nowrap">Amount / contract size 會是最終下單的 Quantity。</div>
                    <div className="whitespace-nowrap">用於提升系統內部浮點數運算效率。</div>
                    <div className="h-px bg-white/20 my-1" />
                    <div className="whitespace-nowrap font-bold mb-1">Order Quantity Explanation</div>
                    <div className="whitespace-nowrap">Amount / contract size results in the final order Quantity.</div>
                    <div className="whitespace-nowrap">Designed to improve internal floating-point calculation efficiency.</div>
                  </div>
                )}
                placement="bottomLeft"
                classNames={{ root: 'liquid-tooltip' }}
                styles={{ root: { maxWidth: 'none' }, body: { maxWidth: 'none' } }}
              >
                <div className="liquid-tooltip-trigger w-3 h-3 rounded-full bg-slate-100 flex items-center justify-center text-[9px] text-slate-400 cursor-help border border-slate-200">?</div>
              </Tooltip>
            </div>
            <div className="liquid-input-group border-slate-300">
                <input
                  type="number"
                  value={quantity}
                  onChange={(e) => setQuantity(e.target.value)}
                  className="flex-1 bg-transparent border-none outline-none py-1.5 pl-3 pr-2 text-right font-mono text-sm text-slate-700 min-w-0"
                  placeholder="0.00"
                />
                <div className="flex items-center justify-start w-[60px] pl-3 border-l border-slate-200 bg-slate-50/50">
                  <span className="text-xs text-slate-500 font-bold whitespace-nowrap select-none">
                    {instrument?.baseAsset || ''}
                  </span>
                </div>
            </div>
            <div className="text-[11px] text-slate-400 text-right pr-2">
              Contract Size: {instrument?.contractSize ?? '--'}
            </div>
            <div className="text-[11px] text-slate-400 text-right pr-2">
              Quantity: {instrument ? displayNormalizedQuantity : '--'}
            </div>
        </div>

        {/* Total Value */}
        <div className="space-y-2 pt-2">
            <div className="flex justify-between text-xs text-slate-500">
              <span>Leverage</span>
              <span className="text-slate-700 font-medium">
                {displayLeverage !== null ? `${displayLeverage}x` : '--'}
              </span>
            </div>
            <div className="flex justify-between text-xs text-slate-500">
              <span>Initial Margin Rate</span>
              <span className="text-slate-700 font-medium">{formatRate(riskLimit?.initialMarginRate)}</span>
            </div>
            <div className="flex justify-between text-xs text-slate-500">
              <span>Maintenance Margin Rate</span>
              <span className="text-slate-700 font-medium">{formatRate(riskLimit?.maintenanceMarginRate)}</span>
            </div>
            <div className="border-t border-white/40" />
            <div className="flex justify-between text-xs text-slate-500">
              <div className="flex items-center gap-1">
                <span>Estimated Fee</span>
                <Tooltip
                  title={(
                    <div className="text-xs">
                      <div className="whitespace-nowrap font-bold mb-1">手續費說明</div>
                      <div className="whitespace-nowrap">下單將以 Taker Fee 預留手續費。</div>
                      <div className="whitespace-nowrap">結算時若成交為 Maker 將退回差額。</div>
                      <div className="h-px bg-white/20 my-1" />
                      <div className="whitespace-nowrap font-bold mb-1">Fee Explanation</div>
                      <div className="whitespace-nowrap">Taker fee is reserved upon order placement.</div>
                      <div className="whitespace-nowrap">The difference will be refunded if filled as a Maker.</div>
                    </div>
                  )}
                  placement="bottomRight"
                  classNames={{ root: 'liquid-tooltip' }}
                  styles={{ root: { maxWidth: 'none' }, body: { maxWidth: 'none' } }}
                >
                  <div className="liquid-tooltip-trigger w-3 h-3 rounded-full bg-slate-100 flex items-center justify-center text-[9px] text-slate-400 cursor-help border border-slate-200">?</div>
                </Tooltip>
              </div>
              <span className="text-slate-700 font-medium">
                {instrument ? `${Number(estimatedFee.toFixed(6))} ${instrument.quoteAsset || ''}` : '--'}
              </span>
            </div>
            <div className="flex justify-between text-xs text-slate-500">
              <span>TOTAL</span>
              <span className="text-slate-700 font-medium">
                {instrument ? `${Number(total.toFixed(6))} ${instrument.quoteAsset || ''}` : '--'}
              </span>
            </div>
        </div>

        {/* Submit Button */}
        <button 
          onClick={handleSubmit}
          disabled={loading}
          className={`w-full py-3 mt-4 rounded-xl text-sm font-bold text-white shadow-lg transition-all transform active:scale-[0.98] hover:-translate-y-0.5 disabled:opacity-50 disabled:cursor-not-allowed ${
            side === 'BUY' 
              ? 'bg-gradient-to-r from-emerald-500 to-emerald-400 shadow-emerald-500/30' 
              : 'bg-gradient-to-r from-rose-500 to-rose-400 shadow-rose-500/30'
          }`}
        >
          {loading ? 'Processing...' : (side === 'BUY' ? 'Buy / Long' : 'Sell / Short')}
        </button>
      </div>
    </div>
  );
}
