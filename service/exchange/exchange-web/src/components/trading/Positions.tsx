

export default function Positions() {
  return (
    <div className="liquid-card mt-6 flex-1 flex flex-col min-h-[250px] overflow-hidden">
        <div className="flex items-center gap-6 border-b border-white/20 px-6 py-3 overflow-x-auto bg-white/10 backdrop-blur-md">
            {['Positions', 'Open Orders', 'History', 'Assets'].map((t, i) => (
                <button key={t} className={`text-sm font-medium whitespace-nowrap transition-colors relative pb-1 ${i === 0 ? 'text-slate-800 font-bold' : 'text-slate-500 hover:text-slate-700'}`}>
                    {t}
                    {i === 0 && <span className="absolute -bottom-[17px] left-0 right-0 h-[3px] bg-blue-500 rounded-t-full shadow-[0_0_12px_rgba(59,130,246,0.6)]" />}
                </button>
            ))}
        </div>

        <div className="p-0 overflow-x-auto">
            <table className="w-full text-sm text-left text-slate-600">
                <thead className="bg-white/5">
                    <tr className="text-xs text-slate-400 uppercase tracking-wider border-b border-white/20">
                        <th className="py-3 pl-6 font-medium">Instrument</th>
                        <th className="py-3 font-medium">Size</th>
                        <th className="py-3 font-medium">Entry Price</th>
                        <th className="py-3 font-medium">Mark Price</th>
                        <th className="py-3 font-medium">PnL (ROE%)</th>
                        <th className="py-3 text-right pr-6 font-medium">Action</th>
                    </tr>
                </thead>
                <tbody className="divide-y divide-white/10">
                    <tr className="group hover:bg-white/20 transition-colors">
                        <td className="py-4 pl-6">
                            <div className="flex items-center gap-3">
                                <div className="w-1 h-8 rounded-full bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.4)]"></div>
                                <div>
                                    <div className="font-bold text-slate-800 text-sm">BTC/USDT</div>
                                    <div className="text-[10px] font-bold text-emerald-600 bg-emerald-100/50 px-1.5 py-0.5 rounded border border-emerald-200/50 w-fit mt-0.5">20x Long</div>
                                </div>
                            </div>
                        </td>
                        <td className="py-4 font-mono text-slate-700 font-medium">0.500 BTC</td>
                        <td className="py-4 font-mono text-slate-500">65,240.50</td>
                        <td className="py-4 font-mono text-slate-800">67,242.15</td>
                        <td className="py-4">
                            <div className="text-emerald-600 font-bold font-mono">+1,000.82</div>
                            <div className="text-[10px] text-emerald-500 font-mono font-medium">+15.2%</div>
                        </td>
                        <td className="py-4 text-right pr-6">
                            <button className="text-xs font-semibold px-3 py-1.5 rounded-lg border border-white/40 bg-white/40 hover:bg-white/60 text-slate-600 hover:text-slate-800 shadow-sm transition-all hover:shadow-md active:scale-95">Close Position</button>
                        </td>
                    </tr>
                     <tr className="group hover:bg-white/20 transition-colors">
                        <td className="py-4 pl-6">
                            <div className="flex items-center gap-3">
                                <div className="w-1 h-8 rounded-full bg-rose-500 shadow-[0_0_8px_rgba(244,63,94,0.4)]"></div>
                                <div>
                                    <div className="font-bold text-slate-800 text-sm">ETH/USDT</div>
                                    <div className="text-[10px] font-bold text-rose-600 bg-rose-100/50 px-1.5 py-0.5 rounded border border-rose-200/50 w-fit mt-0.5">10x Short</div>
                                </div>
                            </div>
                        </td>
                        <td className="py-4 font-mono text-slate-700 font-medium">5.000 ETH</td>
                        <td className="py-4 font-mono text-slate-500">3,450.20</td>
                        <td className="py-4 font-mono text-slate-800">3,420.10</td>
                        <td className="py-4">
                            <div className="text-emerald-600 font-bold font-mono">+150.50</div>
                            <div className="text-[10px] text-emerald-500 font-mono font-medium">+0.87%</div>
                        </td>
                        <td className="py-4 text-right pr-6">
                            <button className="text-xs font-semibold px-3 py-1.5 rounded-lg border border-white/40 bg-white/40 hover:bg-white/60 text-slate-600 hover:text-slate-800 shadow-sm transition-all hover:shadow-md active:scale-95">Close Position</button>
                        </td>
                    </tr>
                </tbody>
            </table>

            <div className="py-12 text-center">
                <div className="text-slate-400 text-xs italic">End of active positions</div>
            </div>
        </div>
    </div>
  );
}
