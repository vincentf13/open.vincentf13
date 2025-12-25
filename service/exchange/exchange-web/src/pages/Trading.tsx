export default function Trading() {
  return (
    <div className="min-h-screen bg-gradient-to-b from-[#D6E4F0] to-[#AEC2D6] text-slate-700">
      <div className="mx-auto max-w-6xl px-4 py-10 lg:px-6">
        <div className="liquid-shell">
          <div className="liquid-shell-content flex flex-col gap-5 px-5 pb-6 pt-5 lg:px-6">
            <header className="flex flex-wrap items-center justify-between gap-4 px-2 pt-1">
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

              <section className="liquid-embed">
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
                  <div className="liquid-embed px-3 py-3 text-xs">
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
                  <div className="liquid-embed px-3 py-3">
                    <div className="relative rounded-lg border border-white/60 bg-white/75 px-2 py-2">
                      <div className="absolute left-3 top-2 text-sm font-semibold text-blue-600">$67,243.15</div>
                      <div className="pointer-events-none absolute right-2 top-6 flex h-[150px] flex-col justify-between text-[10px] text-slate-400">
                        <span>67,320</span>
                        <span>67,280</span>
                        <span>67,240</span>
                        <span>67,200</span>
                        <span>67,160</span>
                      </div>
                      <svg className="h-56 w-full" viewBox="0 0 640 240" preserveAspectRatio="none">
                        <g stroke="#d7e6f5" strokeWidth="1">
                          <line x1="0" y1="30" x2="640" y2="30" />
                          <line x1="0" y1="70" x2="640" y2="70" />
                          <line x1="0" y1="110" x2="640" y2="110" />
                          <line x1="0" y1="150" x2="640" y2="150" />
                          <line x1="0" y1="190" x2="640" y2="190" />
                          <line x1="80" y1="20" x2="80" y2="200" />
                          <line x1="160" y1="20" x2="160" y2="200" />
                          <line x1="240" y1="20" x2="240" y2="200" />
                          <line x1="320" y1="20" x2="320" y2="200" />
                          <line x1="400" y1="20" x2="400" y2="200" />
                          <line x1="480" y1="20" x2="480" y2="200" />
                          <line x1="560" y1="20" x2="560" y2="200" />
                        </g>
                        <g stroke="#3d74b5" strokeWidth="1.8">
                          <line x1="40" y1="130" x2="40" y2="170" />
                          <line x1="80" y1="140" x2="80" y2="182" />
                          <line x1="120" y1="150" x2="120" y2="188" />
                          <line x1="160" y1="128" x2="160" y2="170" />
                          <line x1="200" y1="120" x2="200" y2="160" />
                          <line x1="240" y1="108" x2="240" y2="150" />
                          <line x1="280" y1="98" x2="280" y2="140" />
                          <line x1="320" y1="90" x2="320" y2="130" />
                          <line x1="360" y1="84" x2="360" y2="122" />
                          <line x1="400" y1="76" x2="400" y2="114" />
                          <line x1="440" y1="66" x2="440" y2="106" />
                          <line x1="480" y1="58" x2="480" y2="96" />
                          <line x1="520" y1="50" x2="520" y2="90" />
                          <line x1="560" y1="42" x2="560" y2="82" />
                          <line x1="600" y1="36" x2="600" y2="76" />
                        </g>
                        <g fill="#f4f9ff" stroke="#3d74b5" strokeWidth="1.4">
                          <rect x="34" y="142" width="12" height="16" rx="2" />
                          <rect x="74" y="152" width="12" height="18" rx="2" />
                          <rect x="114" y="158" width="12" height="18" rx="2" />
                          <rect x="154" y="136" width="12" height="18" rx="2" />
                          <rect x="194" y="126" width="12" height="18" rx="2" />
                          <rect x="234" y="116" width="12" height="18" rx="2" />
                          <rect x="274" y="106" width="12" height="18" rx="2" />
                          <rect x="314" y="98" width="12" height="18" rx="2" />
                          <rect x="354" y="92" width="12" height="18" rx="2" />
                          <rect x="394" y="84" width="12" height="18" rx="2" />
                          <rect x="434" y="74" width="12" height="18" rx="2" />
                          <rect x="474" y="66" width="12" height="18" rx="2" />
                          <rect x="514" y="58" width="12" height="18" rx="2" />
                          <rect x="554" y="50" width="12" height="18" rx="2" />
                          <rect x="594" y="44" width="12" height="18" rx="2" />
                        </g>
                        <g fill="#3d74b5" opacity="0.28">
                          <rect x="30" y="206" width="12" height="22" />
                          <rect x="60" y="202" width="12" height="26" />
                          <rect x="90" y="198" width="12" height="30" />
                          <rect x="120" y="204" width="12" height="24" />
                          <rect x="150" y="194" width="12" height="34" />
                          <rect x="180" y="200" width="12" height="28" />
                          <rect x="210" y="190" width="12" height="38" />
                          <rect x="240" y="204" width="12" height="24" />
                          <rect x="270" y="198" width="12" height="30" />
                          <rect x="300" y="206" width="12" height="22" />
                          <rect x="330" y="200" width="12" height="28" />
                          <rect x="360" y="196" width="12" height="32" />
                        </g>
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
                <div className="liquid-embed">
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

                <div className="liquid-embed">
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
                      <div className="liquid-embed p-2">
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

              <section className="liquid-embed">
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
                  <div className="liquid-embed p-4 text-sm text-slate-600">
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

              <section className="liquid-embed">
                <div className="border-b border-white/60 px-4 py-3 text-sm font-semibold text-slate-800">Market Stats</div>
                <div className="px-4 py-4 text-sm text-slate-600">
                  <div className="liquid-embed p-2">
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
  );
}
