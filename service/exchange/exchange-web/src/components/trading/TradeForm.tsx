import { useState } from 'react';

export default function TradeForm() {
  const [side, setSide] = useState<'buy' | 'sell'>('buy');

  return (
    <div className="flex flex-col h-full bg-white/5 border-t border-white/20">
      <div className="flex items-center gap-2 p-3 border-b border-white/20">
        <button
          onClick={() => setSide('buy')}
          className={`flex-1 py-2 rounded-xl text-sm font-semibold transition-all ${side === 'buy' ? 'bg-emerald-500 text-white shadow-lg shadow-emerald-500/20' : 'bg-white/30 text-slate-600 hover:bg-white/50'}`}
        >
          Buy
        </button>
        <button
          onClick={() => setSide('sell')}
          className={`flex-1 py-2 rounded-xl text-sm font-semibold transition-all ${side === 'sell' ? 'bg-rose-500 text-white shadow-lg shadow-rose-500/20' : 'bg-white/30 text-slate-600 hover:bg-white/50'}`}
        >
          Sell
        </button>
      </div>

      <div className="p-4 space-y-4">
        <div className="space-y-1.5">
            <label className="text-xs font-medium text-slate-500 uppercase tracking-wider">Type</label>
            <div className="grid grid-cols-3 gap-1 bg-white/20 p-1 rounded-xl border border-white/30">
                {['Limit', 'Market', 'Stop'].map(t => (
                    <button key={t} className={`text-xs py-1.5 rounded-lg transition-all ${t === 'Limit' ? 'bg-white shadow-sm text-slate-800 font-semibold' : 'text-slate-500 hover:text-slate-700'}`}>
                        {t}
                    </button>
                ))}
            </div>
        </div>

        <div className="space-y-1.5">
            <label className="text-xs font-medium text-slate-500 uppercase tracking-wider">Price (USDT)</label>
            <div className="relative group">
                <input type="text" className="liquid-input pl-3 pr-12 text-right font-mono" defaultValue="67,242.15" />
                <button className="absolute left-2 top-1.5 text-[10px] text-slate-400 border border-slate-300 rounded px-1 hover:bg-white hover:text-slate-600 transition-colors">Best</button>
            </div>
        </div>

        <div className="space-y-1.5">
            <label className="text-xs font-medium text-slate-500 uppercase tracking-wider">Amount (BTC)</label>
            <div className="relative group">
                <input type="text" className="liquid-input pl-3 pr-12 text-right font-mono" placeholder="0.00" />
                <span className="absolute right-3 top-2.5 text-xs text-slate-400 font-medium group-focus-within:text-blue-500 transition-colors">BTC</span>
            </div>
        </div>

        <div className="space-y-2 pt-2">
            <div className="flex justify-between text-xs text-slate-500">
                <span>Avail</span>
                <span className="text-slate-700 font-medium">24,050.50 USDT</span>
            </div>
            <div className="w-full h-1.5 bg-white/30 rounded-full overflow-hidden">
                <div className="w-[45%] h-full bg-blue-400 rounded-full shadow-[0_0_10px_rgba(96,165,250,0.5)]"></div>
            </div>
        </div>

        <button className={`w-full py-3 mt-4 rounded-xl text-sm font-bold text-white shadow-lg transition-all transform active:scale-[0.98] hover:-translate-y-0.5 ${side === 'buy' ? 'bg-gradient-to-r from-emerald-500 to-emerald-400 shadow-emerald-500/30' : 'bg-gradient-to-r from-rose-500 to-rose-400 shadow-rose-500/30'}`}>
            {side === 'buy' ? 'Buy / Long BTC' : 'Sell / Short BTC'}
        </button>
      </div>
    </div>
  );
}
