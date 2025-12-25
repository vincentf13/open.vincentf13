

export default function MarketStats() {
    return (
        <div className="p-4">
            <div className="grid grid-cols-1 lg:grid-cols-[auto,1fr] gap-4 items-start">
                <div className="flex flex-col gap-2">
                    <div className="flex items-center gap-2">
                        <span className="text-sm font-bold text-slate-800">BTC/USDT</span>
                        <span className="text-[10px] uppercase text-slate-500 font-semibold tracking-wider">Perpetual</span>
                        <span className="text-[10px] uppercase text-slate-600 font-semibold tracking-wider bg-white/40 border border-white/50 rounded-md px-1.5 py-0.5">USDT-M</span>
                    </div>
                    <div className="flex items-center gap-3">
                        <span className="text-2xl font-bold text-slate-800">$67,242.15</span>
                        <span className="text-sm font-medium text-emerald-600 bg-emerald-400/20 px-2 py-0.5 rounded-lg border border-emerald-400/30 flex items-center gap-1 shadow-sm">
                            ▲ 2.4%
                        </span>
                    </div>
                </div>

                <div className="grid grid-cols-2 sm:grid-cols-4 gap-x-6 gap-y-3 text-xs text-slate-600">
                    <div className="flex items-center gap-2">
                        <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider">Mark</span>
                        <span className="font-mono font-medium text-slate-700">67,242.15</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider">Index</span>
                        <span className="font-mono font-medium text-slate-700">67,230.80</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider">Funding</span>
                        <span className="font-mono font-medium text-orange-500">0.0100%</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider">Next</span>
                        <span className="font-mono font-medium text-slate-600">01:52:10</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider">24h High</span>
                        <span className="font-mono font-medium text-slate-700">67,880.20</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider">24h Low</span>
                        <span className="font-mono font-medium text-slate-700">66,210.40</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider">24h Vol</span>
                        <span className="font-mono font-medium text-slate-700">18,240.5 BTC</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider">24h Turnover</span>
                        <span className="font-mono font-medium text-slate-700">$1.18B</span>
                    </div>
                </div>
            </div>
        </div>
    )
}
