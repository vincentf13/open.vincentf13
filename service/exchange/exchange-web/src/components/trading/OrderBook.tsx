

export default function OrderBook() {
  const asks = [
    { p: 67245.5, a: 0.452, t: 30394 },
    { p: 67245.0, a: 0.125, t: 8405 },
    { p: 67244.5, a: 0.850, t: 57158 },
    { p: 67244.0, a: 0.334, t: 22459 },
    { p: 67243.5, a: 1.205, t: 81029 },
  ];
  const bids = [
    { p: 67242.0, a: 0.552, t: 37117 },
    { p: 67241.5, a: 0.225, t: 15129 },
    { p: 67241.0, a: 0.950, t: 63878 },
    { p: 67240.5, a: 0.434, t: 29182 },
    { p: 67240.0, a: 2.105, t: 141540 },
  ];

  const Row = ({ price, amount, total, type }: { price: number, amount: number, total: number, type: 'ask' | 'bid' }) => (
    <div className="grid grid-cols-3 text-xs py-1 hover:bg-white/30 cursor-pointer rounded-md px-2 transition-all relative overflow-hidden group">
        {/* Depth Bar */}
        <div
          className={`absolute top-0 bottom-0 right-0 opacity-20 transition-all duration-500 ${type === 'ask' ? 'bg-rose-500' : 'bg-emerald-500'}`}
          style={{ width: `${Math.random() * 60 + 10}%` }}
        />

        <span className={`font-medium z-10 ${type === 'ask' ? 'text-rose-500' : 'text-emerald-600'}`}>
            {price.toFixed(1)}
        </span>
        <span className="text-right text-slate-600 z-10">{amount.toFixed(3)}</span>
        <span className="text-right text-slate-400 z-10">{total.toLocaleString()}</span>
    </div>
  );

  return (
    <div className="flex flex-col h-full w-[80%] ml-auto">
      <div className="p-4 border-b border-white/20 bg-white/10">
          <h3 className="text-sm font-semibold text-slate-700">Order Book</h3>
      </div>

      <div className="flex-1 overflow-hidden p-2">
          <div className="grid grid-cols-3 text-[10px] uppercase text-slate-400 font-bold tracking-wider px-2 mb-2">
              <span>Price</span>
              <span className="text-right">Amount</span>
              <span className="text-right">Total</span>
          </div>

          <div className="space-y-0.5">
              {asks.slice().reverse().map((o, i) => <Row key={i} price={o.p} amount={o.a} total={o.t} type="ask" />)}
          </div>

          <div className="my-3 flex items-center justify-between px-3 py-2 rounded-lg border border-white/40 bg-white/30 backdrop-blur-sm shadow-inner">
              <span className="text-lg font-bold text-slate-800 tracking-tight">67,243.5</span>
              <span className="text-xs text-slate-500 font-medium">≈ $67,243.5</span>
          </div>

          <div className="space-y-0.5">
              {bids.map((o, i) => <Row key={i} price={o.p} amount={o.a} total={o.t} type="bid" />)}
          </div>
      </div>
    </div>
  );
}
