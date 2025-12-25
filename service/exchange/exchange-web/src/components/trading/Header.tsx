

export default function Header() {
  return (
    <header className="flex flex-wrap items-center justify-between gap-4 px-6 py-4 border-b border-white/20 bg-white/10 backdrop-blur-md">
      <div className="flex items-center gap-4">
        <div className="flex h-10 w-10 items-center justify-center rounded-xl border border-white/50 bg-gradient-to-br from-white/60 to-white/20 shadow-sm backdrop-blur-md">
          <div className="h-5 w-5 rounded-full bg-gradient-to-tr from-blue-500 to-cyan-400 shadow-inner ring-2 ring-white/50" />
        </div>
        <div>
          <h1 className="text-xl font-bold tracking-tight text-slate-800 flex items-center gap-2">
            Liquid Flow
            <span className="rounded-full bg-blue-100/50 px-2 py-0.5 text-[9px] font-bold text-blue-600 uppercase tracking-wider border border-blue-200/50">Beta</span>
          </h1>
        </div>
      </div>

      <div className="flex items-center gap-4">
         <div className="flex items-center gap-3">
            <div className="text-right hidden sm:block">
                <div className="text-xs font-medium text-slate-500">Portfolio Value</div>
                <div className="text-sm font-bold text-slate-800">1.245 BTC</div>
            </div>
            <div className="h-10 w-10 rounded-xl bg-gradient-to-br from-slate-700 to-slate-900 shadow-lg border border-white/20 flex items-center justify-center text-white font-bold text-xs cursor-pointer hover:scale-105 transition-transform">
                VF
            </div>
         </div>
      </div>
    </header>
  );
}
