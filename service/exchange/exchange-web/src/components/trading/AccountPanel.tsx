export default function AccountPanel() {
  return (
    <div className="flex flex-col gap-4 p-4">
      <div className="flex items-center justify-between">
        <div>
          <div className="text-sm font-semibold text-slate-700">Account</div>
          <div className="text-[10px] uppercase text-slate-400 font-bold tracking-wider">USDT-M Futures</div>
        </div>
        <button className="text-xs font-semibold px-3 py-1.5 rounded-lg border border-white/30 bg-white/20 hover:bg-white/40 text-slate-600 hover:text-slate-800 transition-all active:scale-95">
          Transfer
        </button>
      </div>

      <div className="space-y-3 text-xs">
        <div className="flex items-center justify-between gap-2 whitespace-nowrap">
          <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider truncate">Wallet Balance</span>
          <span className="text-sm font-mono font-medium text-slate-700 truncate">24,050.50 USDT</span>
        </div>
        <div className="flex items-center justify-between gap-2 whitespace-nowrap">
          <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider truncate">Available</span>
          <span className="text-sm font-mono font-medium text-slate-700 truncate">19,800.30 USDT</span>
        </div>
        <div className="flex items-center justify-between gap-2 whitespace-nowrap">
          <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider truncate">Margin Balance</span>
          <span className="text-sm font-mono font-medium text-slate-700 truncate">21,560.10 USDT</span>
        </div>
        <div className="flex items-center justify-between gap-2 whitespace-nowrap">
          <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider truncate">Unrealized PnL</span>
          <span className="text-sm font-mono font-medium text-emerald-600 truncate">+250.30 USDT</span>
        </div>
        <div className="flex items-center justify-between gap-2 whitespace-nowrap">
          <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider truncate">Maintenance Margin</span>
          <span className="text-sm font-mono font-medium text-slate-700 truncate">1,240.00 USDT</span>
        </div>
      </div>
    </div>
  );
}
