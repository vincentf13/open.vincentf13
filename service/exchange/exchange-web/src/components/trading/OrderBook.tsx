
import { useMemo } from 'react';

type OrderBookProps = {
  instrumentId: string | null;
};

type RowData = {
  price: number;
  amount: number;
  total: number;
  depth: number;
};

type RandomLevel = {
  price: number;
  amount: number;
};

const formatPrice = (value: number) => {
  return value.toLocaleString(undefined, {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  });
};

const formatAmount = (value: number) => {
  return value.toLocaleString(undefined, {
    minimumFractionDigits: 3,
    maximumFractionDigits: 3,
  });
};

const formatTotal = (value: number) => {
  return value.toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
};

const buildRows = (levels: RandomLevel[], sortDesc: boolean) => {
  const sorted = [...levels].sort((a, b) => (sortDesc ? b.price - a.price : a.price - b.price));

  let cumulative = 0;
  const rows = sorted.map((item) => {
    cumulative += item.amount;
    return {
      price: item.price,
      amount: item.amount,
      total: cumulative,
      depth: 0,
    };
  });
  const maxTotal = rows.length ? rows[rows.length - 1].total : 0;
  return rows.map((row) => ({
    ...row,
    depth: maxTotal > 0 ? Math.min(100, (row.total / maxTotal) * 100) : 0,
  }));
};

export default function OrderBook({ instrumentId }: OrderBookProps) {
  const seed = useMemo(() => {
    if (!instrumentId) {
      return 67240;
    }
    let hash = 0;
    for (let i = 0; i < instrumentId.length; i += 1) {
      hash = (hash * 31 + instrumentId.charCodeAt(i)) % 1000;
    }
    return 67000 + hash;
  }, [instrumentId]);

  const { asks, bids, midPrice } = useMemo(() => {
    const base = seed + Math.random() * 30 - 15;
    const makeLevels = (count: number, direction: 1 | -1) => {
      return Array.from({ length: count }, (_, index) => {
        const price = base + direction * (index + 1) * (0.5 + Math.random() * 0.4);
        const amount = Math.max(0.05, 0.2 + Math.random() * 1.6);
        return { price, amount };
      });
    };
    const askLevels = makeLevels(5, 1);
    const bidLevels = makeLevels(5, -1);
    return {
      asks: buildRows(askLevels, false),
      bids: buildRows(bidLevels, true),
      midPrice: base,
    };
  }, [seed]);

  const displayAsks = useMemo(() => asks.slice().reverse(), [asks]);

  const Row = ({ price, amount, total, depth, type }: { price: number; amount: number; total: number; depth: number; type: 'ask' | 'bid' }) => (
    <div className="grid grid-cols-3 text-xs py-1 hover:bg-white/30 cursor-pointer rounded-md px-2 transition-all relative overflow-hidden group">
        <div
          className={`absolute top-0 bottom-0 right-0 opacity-20 transition-all duration-500 ${type === 'ask' ? 'bg-rose-500' : 'bg-emerald-500'}`}
          style={{ width: `${depth}%` }}
        />

        <span className={`font-medium z-10 ${type === 'ask' ? 'text-rose-500' : 'text-emerald-600'}`}>
            {formatPrice(price)}
        </span>
        <span className="text-right text-slate-600 z-10">{formatAmount(amount)}</span>
        <span className="text-right text-slate-400 z-10">{formatTotal(total)}</span>
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
              {displayAsks.length ? displayAsks.map((o, i) => (
                <Row key={`ask-${i}`} price={o.price} amount={o.amount} total={o.total} depth={o.depth} type="ask" />
              )) : (
                <div className="px-2 py-6 text-xs text-slate-400 text-center">No data</div>
              )}
          </div>

          <div className="my-3 flex items-center justify-between px-2 py-1 text-slate-700">
              <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider">Mid Price</span>
              <span className="text-sm font-semibold tracking-tight">
                {midPrice !== null ? formatPrice(midPrice) : '-'}
              </span>
          </div>

          <div className="space-y-0.5">
              {bids.length ? bids.map((o, i) => (
                <Row key={`bid-${i}`} price={o.price} amount={o.amount} total={o.total} depth={o.depth} type="bid" />
              )) : (
                <div className="px-2 py-6 text-xs text-slate-400 text-center">No data</div>
              )}
          </div>
      </div>
    </div>
  );
}
