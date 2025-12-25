export default function Trading() {
  return (
    <div className="min-h-screen bg-gradient-to-b from-[#D6E4F0] to-[#AEC2D6] text-slate-700">
      <div className="mx-auto max-w-6xl px-4 py-10 lg:px-6">
        <div className="relative">
          <div className="absolute inset-0 translate-y-4 rounded-lg border border-white/50 bg-white/30 shadow-[0_26px_60px_rgba(66,88,112,0.35)]" />
          <div className="relative overflow-hidden rounded-lg border border-white/70 bg-gradient-to-br from-white/70 via-white/45 to-white/25 shadow-[0_18px_40px_rgba(80,100,124,0.25)]">
            <div className="pointer-events-none absolute inset-0 bg-gradient-to-br from-white/55 via-transparent to-transparent" />
            <div className="relative flex flex-col gap-5 px-5 pb-6 pt-5 lg:px-6">
              <header className="flex flex-wrap items-center justify-between gap-4 rounded-lg border border-white/60 bg-white/50 px-4 py-3">
                <div className="flex items-center gap-3">
                  <div className="flex h-9 w-9 items-center justify-center rounded-lg border border-white/60 bg-white/70">
                    <span className="h-4 w-4 rounded-lg bg-blue-400/80" />
                  </div>
                  <div className="text-sm">
                    <div className="text-base font-semibold text-slate-800">Liquid Flow</div>
                  </div>
                </div>
                <div className="flex items-center gap-2 rounded-lg border border-white/60 bg-white/70 px-3 py-1 text-xs font-semibold text-slate-700">
                  BTC/USD
                  <span className="text-[10px] text-slate-500">15m</span>
                </div>
              </header>

              <section className="rounded-lg border border-white/60 bg-gradient-to-br from-white/70 via-white/45 to-white/25 shadow-[0_10px_24px_rgba(80,100,124,0.15)]">
                <div className="flex flex-wrap items-center justify-between gap-3 border-b border-white/60 px-4 py-3 text-xs font-semibold text-slate-600">
                  <div className="flex items-center gap-3">
                    <span className="text-sm text-slate-800">Order Book</span>
                    <div className="flex items-center gap-2 rounded-lg border border-white/60 bg-white/70 p-1">
                      <span className="rounded-lg bg-white/80 px-3 py-1 text-slate-800">Price</span>
                      <span className="rounded-lg px-3 py-1 text-slate-500">Limit</span>
                    </div>
                  </div>
                  <span className="text-sm font-semibold text-slate-700">$67,242.15</span>
                </div>
                <div className="grid gap-4 px-4 pb-4 pt-3 lg:grid-cols-[220px,1fr]">
                  <div className="rounded-lg border border-white/60 bg-white/55 p-3 text-xs">
                    <div className="grid grid-cols-2 text-[11px] uppercase text-slate-500">
                      <span>Bids</span>
                      <span className="text-right">Amount</span>
                    </div>
                    <div className="mt-3 space-y-2 text-sm text-slate-700">
                      <div className="flex justify-between"><span>67,243.30</span><span>0.038</span></div>
                      <div className="flex justify-between"><span>67,242.80</span><span>0.052</span></div>
                      <div className="flex justify-between"><span>67,242.40</span><span>0.041</span></div>
                      <div className="flex justify-between"><span>67,241.90</span><span>0.063</span></div>
                      <div className="flex justify-between"><span>67,241.40</span><span>0.055</span></div>
                    </div>
                    <div className="mt-3 flex items-center gap-2 rounded-lg border border-white/60 bg-white/70 px-2 py-1 text-[10px] uppercase text-slate-500">
                      <span>Spread</span>
                      <span>$1.20</span>
                    </div>
                  </div>
                  <div className="rounded-lg border border-white/60 bg-white/55 p-3">
                    <div className="relative mt-1">
                      <div className="absolute left-3 top-2 text-sm font-semibold text-blue-600">$67,243.15</div>
                      <div className="pointer-events-none absolute right-2 top-6 flex h-[150px] flex-col justify-between text-[10px] text-slate-400">
                        <span>67,320</span>
                        <span>67,280</span>
                        <span>67,240</span>
                        <span>67,200</span>
                        <span>67,160</span>
                      </div>
                      <svg className="h-52 w-full" viewBox="0 0 640 230" preserveAspectRatio="none">
                        <defs>
                          <linearGradient id="chartFillMain" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="0%" stopColor="#5b93d1" stopOpacity="0.35" />
                            <stop offset="100%" stopColor="#ffffff" stopOpacity="0.05" />
                          </linearGradient>
                        </defs>
                        <g stroke="#d7e3f2" strokeWidth="1">
                          <line x1="0" y1="40" x2="640" y2="40" />
                          <line x1="0" y1="80" x2="640" y2="80" />
                          <line x1="0" y1="120" x2="640" y2="120" />
                          <line x1="0" y1="160" x2="640" y2="160" />
                          <line x1="0" y1="200" x2="640" y2="200" />
                          <line x1="80" y1="20" x2="80" y2="210" />
                          <line x1="160" y1="20" x2="160" y2="210" />
                          <line x1="240" y1="20" x2="240" y2="210" />
                          <line x1="320" y1="20" x2="320" y2="210" />
                          <line x1="400" y1="20" x2="400" y2="210" />
                          <line x1="480" y1="20" x2="480" y2="210" />
                          <line x1="560" y1="20" x2="560" y2="210" />
                        </g>
                        <path
                          d="M20 145 L70 160 L120 170 L170 150 L220 140 L270 122 L320 112 L370 102 L420 96 L470 84 L520 74 L570 60 L620 52"
                          fill="none"
                          stroke="#3d74b5"
                          strokeWidth="2.2"
                        />
                        <path
                          d="M20 145 L70 160 L120 170 L170 150 L220 140 L270 122 L320 112 L370 102 L420 96 L470 84 L520 74 L570 60 L620 52 L620 210 L20 210 Z"
                          fill="url(#chartFillMain)"
                        />
                        <g fill="#f8fbff" stroke="#3d74b5" strokeWidth="1.6">
                          <circle cx="20" cy="145" r="3" />
                          <circle cx="70" cy="160" r="3" />
                          <circle cx="120" cy="170" r="3" />
                          <circle cx="170" cy="150" r="3" />
                          <circle cx="220" cy="140" r="3" />
                          <circle cx="270" cy="122" r="3" />
                          <circle cx="320" cy="112" r="3" />
                          <circle cx="370" cy="102" r="3" />
                          <circle cx="420" cy="96" r="3" />
                          <circle cx="470" cy="84" r="3" />
                          <circle cx="520" cy="74" r="3" />
                          <circle cx="570" cy="60" r="3" />
                          <circle cx="620" cy="52" r="3" />
                        </g>
                      </svg>
                    </div>
                    <div className="mt-2 rounded-lg border border-white/60 bg-white/70 p-2">
                      <svg className="h-10 w-full" viewBox="0 0 640 60" preserveAspectRatio="none">
                        <rect x="10" y="28" width="16" height="26" fill="#3d74b5" opacity="0.35" />
                        <rect x="40" y="22" width="16" height="32" fill="#3d74b5" opacity="0.35" />
                        <rect x="70" y="16" width="16" height="38" fill="#3d74b5" opacity="0.35" />
                        <rect x="100" y="24" width="16" height="30" fill="#3d74b5" opacity="0.35" />
                        <rect x="130" y="12" width="16" height="42" fill="#3d74b5" opacity="0.35" />
                        <rect x="160" y="18" width="16" height="36" fill="#3d74b5" opacity="0.35" />
                        <rect x="190" y="10" width="16" height="44" fill="#3d74b5" opacity="0.35" />
                        <rect x="220" y="22" width="16" height="32" fill="#3d74b5" opacity="0.35" />
                        <rect x="250" y="18" width="16" height="36" fill="#3d74b5" opacity="0.35" />
                        <rect x="280" y="26" width="16" height="28" fill="#3d74b5" opacity="0.35" />
                        <rect x="310" y="20" width="16" height="34" fill="#3d74b5" opacity="0.35" />
                        <rect x="340" y="14" width="16" height="40" fill="#3d74b5" opacity="0.35" />
                      </svg>
                      <div className="mt-2 flex justify-between text-[10px] text-slate-400">
                        <span>00:00</span>
                        <span>04:00</span>
                        <span>08:00</span>
                        <span>12:00</span>
                        <span>16:00</span>
                        <span>20:00</span>
                      </div>
                    </div>
                  </div>
                </div>
              </section>

              <section className="grid gap-4 lg:grid-cols-[1fr,2fr]">
                <div className="rounded-lg border border-white/60 bg-white/45 shadow-[0_10px_22px_rgba(80,100,124,0.12)]">
                  <div className="border-b border-white/60 px-4 py-3 text-sm font-semibold text-slate-800">Order Book</div>
                  <div className="px-4 py-4 text-xs text-slate-600">
                    <div className="grid grid-cols-3 text-[11px] uppercase text-slate-500">
                      <span>Bids</span>
                      <span>Amount</span>
                      <span className="text-right">Total (BTC)</span>
                    </div>
                    <div className="mt-3 space-y-2 text-sm text-slate-700">
                      <div className="grid grid-cols-3"><span>67,242.0</span><span>0.021</span><span className="text-right">0.812</span></div>
                      <div className="grid grid-cols-3"><span>67,238.6</span><span>0.046</span><span className="text-right">0.792</span></div>
                      <div className="grid grid-cols-3"><span>67,233.2</span><span>0.038</span><span className="text-right">0.761</span></div>
                      <div className="grid grid-cols-3"><span>67,229.5</span><span>0.052</span><span className="text-right">0.742</span></div>
                    </div>
                    <div className="mt-4 flex items-center gap-2 rounded-lg border border-white/60 bg-white/70 px-3 py-2 text-[10px] uppercase text-slate-500">
                      <span>Spread</span>
                      <span>$1.20</span>
                    </div>
                  </div>
                </div>

                <div className="rounded-lg border border-white/60 bg-white/45 shadow-[0_10px_22px_rgba(80,100,124,0.12)]">
                  <div className="flex items-center justify-between border-b border-white/60 px-4 py-3 text-sm font-semibold text-slate-800">
                    <span>Position</span>
                    <div className="flex items-center gap-1 rounded-lg border border-white/60 bg-white/70 p-1 text-xs text-slate-600">
                      <span className="rounded-lg bg-white/80 px-2 py-1 text-slate-800">Limit</span>
                      <span className="rounded-lg px-2 py-1">Limit</span>
                      <span className="rounded-lg px-2 py-1">Test</span>
                    </div>
                  </div>
                  <div className="px-4 py-4 text-sm text-slate-600">
                    <div className="grid gap-4 lg:grid-cols-2">
                      <div className="space-y-3">
                        <div className="flex items-center justify-between">
                          <span>Available</span>
                          <span className="font-semibold text-slate-800">0.3200 BTC</span>
                        </div>
                        <div className="flex items-center justify-between">
                          <span>Margin</span>
                          <span className="font-semibold text-slate-800">0.0092</span>
                        </div>
                        <div className="flex items-center justify-between">
                          <span>Liq. Price</span>
                          <span className="font-semibold text-slate-800">$66,500.00</span>
                        </div>
                      </div>
                      <div className="space-y-3">
                        <div className="flex items-center justify-between">
                          <span>Leverage</span>
                          <span className="font-semibold text-slate-800">20x</span>
                        </div>
                        <div className="flex items-center justify-between">
                          <span>Margin</span>
                          <span className="font-semibold text-slate-800">0.0037 BTC</span>
                        </div>
                        <div className="flex items-center justify-between">
                          <span>Liq. Price</span>
                          <span className="font-semibold text-slate-800">$57,450.00</span>
                        </div>
                      </div>
                    </div>
                    <div className="mt-4 grid grid-cols-[1fr,70px] gap-3">
                      <div className="rounded-lg border border-white/60 bg-white/70 p-2">
                        <svg className="h-20 w-full" viewBox="0 0 220 80" preserveAspectRatio="none">
                          <polyline
                            fill="none"
                            stroke="#3d74b5"
                            strokeWidth="2"
                            points="0,62 18,58 36,60 54,44 72,46 90,38 108,40 126,28 144,30 162,22 180,24 200,18 220,12"
                          />
                        </svg>
                      </div>
                      <div className="flex flex-col justify-between text-xs text-slate-500">
                        <span>$56k</span>
                        <span>$60k</span>
                        <span>$64k</span>
                        <span>$68k</span>
                      </div>
                    </div>
                  </div>
                </div>
              </section>

              <section className="rounded-lg border border-white/60 bg-gradient-to-br from-white/70 via-white/45 to-white/25 shadow-[0_10px_24px_rgba(80,100,124,0.15)]">
                <div className="flex items-center justify-between gap-4 border-b border-white/60 px-5 py-3 text-sm font-semibold text-slate-800">
                  <div className="flex items-center gap-2 rounded-lg border border-white/60 bg-white/70 p-1 text-xs text-slate-600">
                    <span className="flex items-center gap-2 rounded-lg bg-white/80 px-3 py-1 text-slate-800">
                      <span className="h-2 w-2 rounded-lg bg-blue-400/80" />
                      Market
                    </span>
                    <span className="flex items-center gap-2 rounded-lg px-3 py-1 text-slate-500">
                      <span className="h-2 w-2 rounded-lg border border-slate-400/60" />
                      Limit
                    </span>
                  </div>
                </div>
                <div className="grid gap-6 px-5 pb-6 pt-4 lg:grid-cols-[1.15fr,0.85fr]">
                  <div className="space-y-4 text-sm text-slate-600">
                    <div className="grid gap-4 lg:grid-cols-2">
                      <div className="space-y-2">
                        <span className="text-xs uppercase tracking-wide text-slate-500">Price</span>
                        <input
                          className="w-full rounded-lg border border-white/60 bg-white/60 px-3 py-2 text-sm text-slate-700"
                          type="text"
                          value="67,242.15"
                          readOnly
                        />
                      </div>
                      <div className="space-y-2">
                        <span className="text-xs uppercase tracking-wide text-slate-500">Amount</span>
                        <div className="flex items-center gap-2">
                          <input
                            className="w-full rounded-lg border border-white/60 bg-white/60 px-3 py-2 text-sm text-slate-700"
                            type="number"
                            placeholder="0.00"
                          />
                          <span className="rounded-lg border border-white/60 bg-white/70 px-2 py-2 text-xs font-semibold text-slate-600">
                            BTC
                          </span>
                        </div>
                      </div>
                    </div>
                    <div className="grid grid-cols-[110px,1fr] items-center gap-3">
                      <span className="text-xs uppercase tracking-wide text-slate-500">Asks</span>
                      <div className="flex items-center gap-3">
                        <input className="w-full accent-blue-500" type="range" min="0" max="100" defaultValue="60" />
                        <span className="rounded-lg border border-white/60 bg-white/70 px-2 py-1 text-xs font-semibold text-slate-600">
                          BTC
                        </span>
                      </div>
                    </div>
                    <div className="grid grid-cols-[110px,1fr] items-center gap-3">
                      <span className="text-xs uppercase tracking-wide text-slate-500">Leverage</span>
                      <div className="flex items-center gap-3">
                        <input className="w-full accent-blue-500" type="range" min="1" max="50" defaultValue="20" />
                        <span className="text-sm font-semibold text-slate-800">20x</span>
                        <span className="rounded-lg border border-white/60 bg-white/70 px-2 py-1 text-xs text-slate-600">
                          2000
                        </span>
                      </div>
                    </div>
                    <div className="grid grid-cols-[110px,1fr] items-start gap-3">
                      <span className="text-xs uppercase tracking-wide text-slate-500">Margin</span>
                      <div className="flex flex-col text-sm text-slate-700">
                        <span className="font-semibold">0.0037 BTC</span>
                        <span className="text-xs text-slate-500">$16,940.50</span>
                      </div>
                    </div>
                  </div>
                  <div className="space-y-4 rounded-lg border border-white/60 bg-white/60 p-4 text-sm text-slate-600">
                    <div className="flex items-center justify-between">
                      <span>Leverage</span>
                      <span className="font-semibold text-slate-800">20x</span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span>Margin</span>
                      <span className="font-semibold text-slate-800">0.0037 BTC</span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span>Fee</span>
                      <span className="font-semibold text-slate-800">$7.90</span>
                    </div>
                    <div className="border-t border-white/60 pt-4">
                      <div className="text-xs uppercase tracking-wide text-slate-500">Est. Cost</div>
                      <div className="text-xl font-semibold text-slate-800">$16,810.50</div>
                    </div>
                    <div className="grid grid-cols-2 gap-3 pt-2">
                      <button className="rounded-lg bg-emerald-500/80 px-4 py-2 text-sm font-semibold text-white shadow-sm" type="button">
                        Buy / Long BTC
                      </button>
                      <button className="rounded-lg bg-rose-500/80 px-4 py-2 text-sm font-semibold text-white shadow-sm" type="button">
                        Sell / Short BTC
                      </button>
                    </div>
                  </div>
                </div>
              </section>

              <section className="rounded-lg border border-white/60 bg-white/45 shadow-[0_10px_22px_rgba(80,100,124,0.12)]">
                <div className="border-b border-white/60 px-4 py-3 text-sm font-semibold text-slate-800">Market Stats</div>
                <div className="px-4 py-4 text-sm text-slate-600">
                  <div className="rounded-lg border border-white/60 bg-white/70 p-2">
                    <svg className="h-16 w-full" viewBox="0 0 200 60" preserveAspectRatio="none">
                      <polyline
                        fill="none"
                        stroke="#3d74b5"
                        strokeWidth="2"
                        points="0,36 20,34 40,28 60,30 80,24 100,26 120,18 140,20 160,14 180,16 200,12"
                      />
                    </svg>
                  </div>
                  <div className="mt-4 space-y-2">
                    <div className="flex items-center justify-between"><span>24h Volume</span><span className="font-semibold text-slate-800">18,240 BTC</span></div>
                    <div className="flex items-center justify-between"><span>24h High</span><span className="font-semibold text-slate-800">$67,880.20</span></div>
                    <div className="flex items-center justify-between"><span>24h Low</span><span className="font-semibold text-slate-800">$66,210.40</span></div>
                  </div>
                </div>
              </section>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
