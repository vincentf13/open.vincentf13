import { useState, useEffect } from 'react';
import { Tooltip } from 'antd';
import { createOrder, type OrderSide, type OrderType } from '../../api/order';
import type { InstrumentSummary } from '../../api/instrument';

interface TradeFormProps {
  instrument: InstrumentSummary | null;
  onOrderPlaced?: () => void;
}

export default function TradeForm({ instrument, onOrderPlaced }: TradeFormProps) {
  const [side, setSide] = useState<OrderSide>('BUY');
  const [type, setType] = useState<OrderType>('LIMIT');
  const [price, setPrice] = useState<string>('');
  const [quantity, setQuantity] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [amountTooltipHovered, setAmountTooltipHovered] = useState(false);
  const [amountTooltipFocused, setAmountTooltipFocused] = useState(false);

  // Reset form when instrument changes
  useEffect(() => {
    setPrice('');
    setQuantity('');
  }, [instrument?.instrumentId]);

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
      const result = await createOrder({
        instrumentId: Number(instrument.instrumentId),
        side,
        type,
        quantity: normalizedQuantity,
        price: type === 'LIMIT' ? Number(price) : null,
      });
      if (String(result?.code) !== '0') {
        alert(`Error: ${result?.message || 'Failed to place order'}`);
        return;
      }
      onOrderPlaced?.();
      alert('Order placed successfully!');
      setQuantity('');
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
  const total = baseTotal + estimatedFee;

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
            <label className="text-xs font-medium text-slate-500 uppercase tracking-wider">Price</label>
            <div className={`liquid-input-group ${type === 'MARKET' ? 'bg-slate-100 cursor-not-allowed' : ''}`}>
                <input 
                  type="number" 
                  step="0.01"
                  value={type === 'MARKET' ? '' : price}
                  onChange={(e) => setPrice(e.target.value)}
                  disabled={type === 'MARKET'}
                  placeholder={type === 'MARKET' ? 'Market Price' : '0.00'}
                  className={`flex-1 bg-transparent border-none outline-none py-2 pl-3 pr-1 text-right font-mono min-w-0 ${
                    type === 'MARKET' ? 'placeholder-slate-400 cursor-not-allowed' : ''
                  }`} 
                />
                <div className="flex items-center justify-end w-[52px] pr-2 border-l border-white/20 bg-white/5">
                  <span className="text-xs text-slate-400 font-medium whitespace-nowrap select-none text-right">
                    {instrument?.quoteAsset || ''}
                  </span>
                </div>
            </div>
        </div>

        {/* Quantity Input */}
        <Tooltip
          title={(
            <div className="text-xs">
              <div className="whitespace-nowrap">Amount * contract size 會是最終下單的 Quantity，用於提升系統內部浮點數運算效率。</div>
              <div className="whitespace-nowrap">下單若是開倉將以 Taker Fee 預留手續費，結算時若為 Maker 將會退回差額。</div>
              <div className="whitespace-nowrap">Amount * contract size becomes the final order Quantity to improve internal floating-point efficiency.</div>
              <div className="whitespace-nowrap">For opening orders, a taker fee is reserved; if filled as maker, the fee difference is refunded on settlement.</div>
            </div>
          )}
          overlayStyle={{ maxWidth: 'none' }}
          overlayInnerStyle={{ maxWidth: 'none' }}
          open={amountTooltipHovered || amountTooltipFocused}
        >
          <div
            className="space-y-1.5"
            onMouseEnter={() => setAmountTooltipHovered(true)}
            onMouseLeave={() => setAmountTooltipHovered(false)}
          >
              <label className="text-xs font-medium text-slate-500 uppercase tracking-wider">Amount</label>
              <div className="liquid-input-group">
                  <input
                    type="number"
                    value={quantity}
                    onChange={(e) => setQuantity(e.target.value)}
                    onFocus={() => setAmountTooltipFocused(true)}
                    onBlur={() => setAmountTooltipFocused(false)}
                    className="flex-1 bg-transparent border-none outline-none py-2 pl-3 pr-1 text-right font-mono min-w-0"
                    placeholder="0.00"
                  />
                  <div className="flex items-center justify-start w-[52px] pl-2 border-l border-white/20 bg-white/5">
                    <span className="text-xs text-slate-400 font-medium whitespace-nowrap select-none">
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
              <div className="text-[11px] text-slate-400 text-right pr-2">
                Estimated Fee: {instrument ? `${Number(estimatedFee.toFixed(6))} ${instrument.quoteAsset || ''}` : '--'}
              </div>
          </div>
        </Tooltip>

        {/* Total Value */}
        <div className="space-y-2 pt-2">
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
