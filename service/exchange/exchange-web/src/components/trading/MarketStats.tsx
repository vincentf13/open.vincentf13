

export default function MarketStats() {
    return (
        <div className="grid grid-cols-2 gap-x-6 gap-y-4 p-5 border-t border-white/20 bg-white/5 backdrop-blur-sm">
            <div className="space-y-1 group">
                <div className="text-[10px] uppercase text-slate-400 font-bold tracking-wider group-hover:text-blue-500 transition-colors">24h High</div>
                <div className="text-sm font-mono font-medium text-slate-700">67,880.20</div>
            </div>
            <div className="space-y-1 group">
                <div className="text-[10px] uppercase text-slate-400 font-bold tracking-wider group-hover:text-blue-500 transition-colors">24h Low</div>
                <div className="text-sm font-mono font-medium text-slate-700">66,210.40</div>
            </div>
            <div className="space-y-1 group">
                <div className="text-[10px] uppercase text-slate-400 font-bold tracking-wider group-hover:text-blue-500 transition-colors">24h Vol (BTC)</div>
                <div className="text-sm font-mono font-medium text-slate-700">18,240.5</div>
            </div>
            <div className="space-y-1 group">
                <div className="text-[10px] uppercase text-slate-400 font-bold tracking-wider group-hover:text-blue-500 transition-colors">Funding / 8h</div>
                <div className="text-sm font-mono font-medium text-orange-500">0.0100%</div>
            </div>
        </div>
    )
}
