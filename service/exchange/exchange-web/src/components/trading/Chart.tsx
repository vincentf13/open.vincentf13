

export default function Chart() {
  return (
    <div className="flex flex-col h-full bg-white/5">
      <div className="flex items-center justify-end border-b border-white/20 px-4 py-3">
        <div className="flex items-center gap-1 bg-white/30 rounded-lg p-1 border border-white/40 shadow-inner">
            {['1H', '4H', '1D', '1W'].map((t, i) => (
                <button key={t} className={`px-3 py-1 text-xs font-medium rounded-md transition-all ${i === 2 ? 'bg-white shadow-sm text-slate-800 font-semibold' : 'text-slate-500 hover:text-slate-700 hover:bg-white/40'}`}>
                    {t}
                </button>
            ))}
        </div>
      </div>

      <div className="relative flex-1 w-full min-h-[300px] p-4">
        {/* Mock Chart Area */}
        <div className="absolute inset-0 top-12 bottom-8 left-4 right-16">
            <svg className="w-full h-full" viewBox="0 0 800 300" preserveAspectRatio="none">
                <defs>
                    <linearGradient id="chartGradient" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="#3B82F6" stopOpacity="0.25"/>
                        <stop offset="100%" stopColor="#3B82F6" stopOpacity="0"/>
                    </linearGradient>
                </defs>

                {/* Grid Lines */}
                <g stroke="rgba(255,255,255,0.4)" strokeDasharray="4 4" strokeWidth="0.5">
                    <line x1="0" y1="75" x2="800" y2="75" />
                    <line x1="0" y1="150" x2="800" y2="150" />
                    <line x1="0" y1="225" x2="800" y2="225" />
                    <line x1="200" y1="0" x2="200" y2="300" />
                    <line x1="400" y1="0" x2="400" y2="300" />
                    <line x1="600" y1="0" x2="600" y2="300" />
                </g>

                {/* Candles (Mock) */}
                <path
                    d="M0,200 C80,190 120,210 160,160 C200,110 240,130 280,140 C320,150 360,120 400,90 C440,60 480,80 520,70 C560,60 600,80 640,50 C680,20 740,30 800,40"
                    fill="none"
                    stroke="#3B82F6"
                    strokeWidth="2.5"
                    className="drop-shadow-sm"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                />
                <path
                    d="M0,200 C80,190 120,210 160,160 C200,110 240,130 280,140 C320,150 360,120 400,90 C440,60 480,80 520,70 C560,60 600,80 640,50 C680,20 740,30 800,40 V300 H0 Z"
                    fill="url(#chartGradient)"
                    stroke="none"
                />
            </svg>

            {/* Price Cursor (Mock) */}
            <div className="absolute top-[13.3%] right-0 w-full border-t border-blue-500/50 border-dashed flex items-center justify-end animate-pulse">
                <span className="bg-blue-500 text-white text-[10px] px-2 py-0.5 rounded-l-md font-mono shadow-sm">67,242.15</span>
            </div>
        </div>

        {/* Y Axis Labels */}
        <div className="absolute right-2 top-12 bottom-8 w-12 flex flex-col justify-between text-[10px] text-slate-400 font-mono text-right select-none">
            <span>68.0k</span>
            <span>67.5k</span>
            <span>67.0k</span>
            <span>66.5k</span>
        </div>
      </div>
    </div>
  );
}
