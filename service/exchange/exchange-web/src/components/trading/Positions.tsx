export default function Positions() {
  return (
    <div className="flex flex-col overflow-hidden bg-white/5">
      <div className="flex items-center justify-between px-4 py-3 border-b border-white/20">
        <div className="flex items-center gap-4 text-xs font-semibold">
          {['Positions', 'Orders', 'Trades'].map((tab, index) => (
            <button
              key={tab}
              className={`uppercase tracking-wider transition-colors ${index === 0 ? 'text-slate-700' : 'text-slate-400 hover:text-slate-600'}`}
            >
              {tab}
            </button>
          ))}
        </div>
      </div>

      <div className="p-4 overflow-x-auto">
        <table className="w-full text-sm text-left text-slate-600">
          <thead>
            <tr className="text-[10px] uppercase text-slate-400 tracking-wider border-b border-white/20">
              <th className="py-2 font-semibold">Instrument</th>
              <th className="py-2 font-semibold">Size</th>
              <th className="py-2 font-semibold">Entry</th>
              <th className="py-2 font-semibold">Mark</th>
              <th className="py-2 font-semibold text-right">PnL</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-white/10">
            <tr className="hover:bg-white/20 transition-colors">
              <td className="py-3 font-semibold text-slate-800">
                <div className="flex items-center gap-2">
                  <span className="h-2 w-2 rounded-full bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.45)]" />
                  BTC/USDT
                </div>
              </td>
              <td className="py-3 font-mono text-slate-700">0.500</td>
              <td className="py-3 font-mono text-slate-500">65,240.50</td>
              <td className="py-3 font-mono text-slate-800">67,242.15</td>
              <td className="py-3 text-right font-mono text-emerald-600">+1,000.82</td>
            </tr>
            <tr className="hover:bg-white/20 transition-colors">
              <td className="py-3 font-semibold text-slate-800">
                <div className="flex items-center gap-2">
                  <span className="h-2 w-2 rounded-full bg-rose-500 shadow-[0_0_8px_rgba(244,63,94,0.4)]" />
                  ETH/USDT
                </div>
              </td>
              <td className="py-3 font-mono text-slate-700">5.000</td>
              <td className="py-3 font-mono text-slate-500">3,450.20</td>
              <td className="py-3 font-mono text-slate-800">3,420.10</td>
              <td className="py-3 text-right font-mono text-emerald-600">+150.50</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
}
