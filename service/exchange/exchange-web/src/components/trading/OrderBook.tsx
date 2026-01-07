import { useEffect, useMemo, useState } from 'react';
import { Tooltip } from 'antd';

import { getOrderBook, type OrderBookLevel, type OrderBookResponse } from '../../api/market';

type OrderBookProps = {
  selectedInstrumentId: string | null;
  refreshTrigger?: number;
  isPaused?: boolean;
  contractSize?: number;
};

type RowData = {
  price: number;
  amount: number;
  total: number;
  depth: number;
};

const toNumber = (value: string | number | null | undefined) => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
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

const buildRows = (levels: OrderBookLevel[] | null | undefined, sortDesc: boolean, contractSize = 1) => {
  if (!levels || !levels.length) {
    return [];
  }
  const sorted = [...levels]
    .map((level) => {
      const q = toNumber(level.quantity) || 0;
      return {
        price: toNumber(level.price),
        amount: q * contractSize,
      };
    })
    .filter((item): item is { price: number; amount: number } => item.price !== null)
    .sort((a, b) => (sortDesc ? b.price - a.price : a.price - b.price));

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

export default function OrderBook({ selectedInstrumentId, refreshTrigger, isPaused, contractSize = 1 }: OrderBookProps) {
  const [orderBook, setOrderBook] = useState<OrderBookResponse | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!selectedInstrumentId) {
      if (!selectedInstrumentId) setOrderBook(null);
      return;
    }
    let cancelled = false;
    const loadOrderBook = async () => {
      setLoading(true);
      try {
        const response = await getOrderBook(selectedInstrumentId);
        if (cancelled) {
          return;
        }
        if (String(response?.code) === '0') {
          setOrderBook(response?.data || null);
        } else {
          setOrderBook(null);
        }
      } catch (error) {
        if (!cancelled) {
          setOrderBook(null);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };
    loadOrderBook();
    return () => {
      cancelled = true;
    };
  }, [selectedInstrumentId, refreshTrigger, isPaused]);

  const asks = useMemo(() => buildRows(orderBook?.asks, false, contractSize).slice(0, 5), [orderBook?.asks, contractSize]);
  const bids = useMemo(() => buildRows(orderBook?.bids, true, contractSize).slice(0, 5), [orderBook?.bids, contractSize]);
  const midPrice = useMemo(() => {
    const mid = toNumber(orderBook?.midPrice);
    if (mid !== null) {
      return mid;
    }
    const bid = toNumber(orderBook?.bestBid);
    const ask = toNumber(orderBook?.bestAsk);
    if (bid !== null && ask !== null) {
      return (bid + ask) / 2;
    }
    return null;
  }, [orderBook?.midPrice, orderBook?.bestBid, orderBook?.bestAsk]);

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
    <div className="relative flex flex-col flex-1 min-h-0 bg-gradient-to-b from-white/15 via-white/5 to-transparent">
      <div className="relative p-4 border-b border-white/20 bg-white/5 flex items-center gap-2">
        <h3 className="text-sm font-semibold text-slate-700">Order Book</h3>
        <Tooltip
          title={(
            <div className="text-xs">
              <div className="whitespace-nowrap font-bold mb-1">訂單簿說明</div>
              <div className="whitespace-nowrap">展示當前市場的買賣盤深度。</div>
              <div className="whitespace-nowrap">採用 LMAX 高性能內存撮合架構，撮合結果異步推送。</div>
              <div className="h-px bg-white/20 my-1" />
              <div className="whitespace-nowrap font-bold mb-1">Order Book Explanation</div>
              <div className="whitespace-nowrap">Displays real-time market depth.</div>
              <div className="whitespace-nowrap">Powered by LMAX-style high-performance in-memory matching.</div>
            </div>
          )}
          placement="bottomRight"
          classNames={{ root: 'liquid-tooltip' }}
          styles={{ root: { maxWidth: 'none' }, body: { maxWidth: 'none' } }}
        >
          <div className="liquid-tooltip-trigger w-4 h-4 rounded-full bg-slate-100 flex items-center justify-center text-[10px] text-slate-400 cursor-help border border-slate-200">?</div>
        </Tooltip>
      </div>

      <div className="relative flex-1 overflow-hidden p-2">
          <div className="grid grid-cols-3 text-[10px] uppercase text-slate-400 font-bold tracking-wider px-2 mb-2 items-center">
              <span>Price</span>
              <span className="text-right">Amount</span>
              <div className="flex items-center justify-end gap-1">
                <span>Total</span>
                <Tooltip
                  title={(
                    <div className="text-xs">
                      <div className="whitespace-nowrap font-bold mb-1">累計總量 (Total)</div>
                      <div className="whitespace-nowrap">計算方式：該檔位及以上所有檔位的 Amount 累加總和</div>
                      <div className="h-px bg-white/20 my-1" />
                      <div className="whitespace-nowrap font-bold mb-1">Cumulative Total</div>
                      <div className="whitespace-nowrap">Sum of all amounts from the best price level down to this level.</div>
                    </div>
                  )}
                  placement="topRight"
                  classNames={{ root: 'liquid-tooltip' }}
                  styles={{ root: { maxWidth: 'none' }, body: { maxWidth: 'none' } }}
                >
                  <div className="liquid-tooltip-trigger w-3 h-3 rounded-full bg-slate-100 flex items-center justify-center text-[9px] text-slate-400 cursor-help border border-slate-200">?</div>
                </Tooltip>
              </div>
          </div>

          <div className="space-y-0.5">
              {displayAsks.length ? displayAsks.map((o, i) => (
                <Row key={`ask-${i}`} price={o.price} amount={o.amount} total={o.total} depth={o.depth} type="ask" />
              )) : (
                <div className="px-2 py-6 text-xs text-slate-400 text-center">{loading ? 'Loading...' : 'No data'}</div>
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
                <div className="px-2 py-6 text-xs text-slate-400 text-center">{loading ? 'Loading...' : 'No data'}</div>
              )}
          </div>
      </div>
    </div>
  );
}
