import { Fragment, useEffect, useMemo, useRef, useState } from 'react';
import { Tooltip, message } from 'antd';

import type { InstrumentSummary } from '../../api/instrument';
import { getOrderEvents, getOrders, type OrderEventItem, type OrderEventResponse, type OrderResponse } from '../../api/order';
import { getPositionEvents, getPositions, type PositionEventItem, type PositionEventResponse, type PositionResponse } from '../../api/position';
import { getTradesByInstrument, getTradesByOrderId, type TradeResponse } from '../../api/trade';
import { getRiskLimit, type RiskLimitResponse } from '../../api/risk';
import { getCurrentUser } from '../../api/user';

type PositionsProps = {
  instruments: InstrumentSummary[];
  selectedInstrumentId: string | null;
  refreshTrigger?: number;
  isPaused?: boolean;
  onSyncComplete?: (name: string) => void;
};
// ... (keeping helper functions)

export default function Positions({ instruments, selectedInstrumentId, refreshTrigger, isPaused, onSyncComplete }: PositionsProps) {
  const [activeTab, setActiveTab] = useState('Positions');
  const [isTabVisible, setIsTabVisible] = useState(true);
  const [positions, setPositions] = useState<PositionResponse[]>([]);
  const [expandedPositionId, setExpandedPositionId] = useState<number | null>(null);
  const [positionEvents, setPositionEvents] = useState<Record<string, PositionEventItem[]>>({});
  const [positionEventsLoading, setPositionEventsLoading] = useState<Record<string, boolean>>({});
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [expandedOrderId, setExpandedOrderId] = useState<number | null>(null);
  const [orderTrades, setOrderTrades] = useState<Record<string, TradeResponse[]>>({});
  const [orderEvents, setOrderEvents] = useState<Record<string, OrderEventItem[]>>({});
  const [orderEventsLoading, setOrderEventsLoading] = useState<Record<string, boolean>>({});
  const [tradeDetail, setTradeDetail] = useState<TradeResponse | null>(null);
  const [tradeDetailLoading, setTradeDetailLoading] = useState(false);
  const [tradeDetailAnchor, setTradeDetailAnchor] = useState<{ top: number; left: number } | null>(null);
  const [instrumentTrades, setInstrumentTrades] = useState<TradeResponse[]>([]);
  const [currentUserId, setCurrentUserId] = useState<string | null>(null);
  const [riskLimits, setRiskLimits] = useState<Record<string, RiskLimitResponse>>({});

  const previousPositionsRef = useRef<Map<string, PositionResponse>>(new Map());
  const positionChangedFieldsRef = useRef<Map<string, Set<string>>>(new Map());
  const previousOrdersRef = useRef<Map<string, OrderResponse>>(new Map());
  const orderChangedFieldsRef = useRef<Map<string, Set<string>>>(new Map());

  useEffect(() => {
    const h = () => setIsTabVisible(document.visibilityState === 'visible');
    document.addEventListener('visibilitychange', h);
    return () => document.removeEventListener('visibilitychange', h);
  }, []);

  const fetchPositions = async () => {
    if (!isTabVisible) {
        onSyncComplete?.('Positions');
        return;
    }
    try {
      const res = await getPositions(selectedInstrumentId || undefined);
      if (String(res?.code) === '0') {
        const next = res.data || [];
        const prevMap = previousPositionsRef.current;
        const nextMap = new Map(next.map((p: any) => [String(p.positionId), p]));
        const changeMap = new Map<string, Set<string>>();
        next.forEach((p: any) => {
          const id = String(p.positionId);
          const prev = prevMap.get(id);
          const changed = new Set<string>();
          positionHighlightKeys.forEach(k => { if (!prev || getCompareValue(p, k) !== getCompareValue(prev, k)) changed.add(k); });
          if (changed.size > 0) changeMap.set(id, changed);
        });
        previousPositionsRef.current = nextMap;
        positionChangedFieldsRef.current = changeMap;
        setPositions(next);
      }
    } catch {
    } finally {
        onSyncComplete?.('Positions');
    }
  };

  const fetchOrders = async () => {
    if (!isTabVisible) {
        onSyncComplete?.('Positions');
        return;
    }
    try {
      const res = await getOrders(selectedInstrumentId || undefined);
      if (String(res?.code) === '0') {
        const next = res.data || [];
        const prevMap = previousOrdersRef.current;
        const nextMap = new Map(next.map((o: any) => [String(o.orderId), o]));
        const changeMap = new Map<string, Set<string>>();
        next.forEach((o: any) => {
          const id = String(o.orderId);
          const prev = prevMap.get(id);
          const changed = new Set<string>();
          orderHighlightKeys.forEach(k => { if (!prev || getCompareValue(o, k) !== getCompareValue(prev, k)) changed.add(k); });
          if (changed.size > 0) changeMap.set(id, changed);
        });
        previousOrdersRef.current = nextMap;
        orderChangedFieldsRef.current = changeMap;
        setOrders(next);
      }
    } catch {
    } finally {
        onSyncComplete?.('Positions');
    }
  };

  const fetchInstrumentTrades = async () => {
    if (!isTabVisible || !selectedInstrumentId) {
        onSyncComplete?.('Positions');
        return;
    }
    try {
      const res = await getTradesByInstrument(selectedInstrumentId);
      if (String(res?.code) === '0') setInstrumentTrades(res.data || []);
    } catch {
    } finally {
        onSyncComplete?.('Positions');
    }
  };
// ... (rest of file)

  useEffect(() => {
    if (activeTab === 'Positions') fetchPositions();
    else if (activeTab === 'Orders') fetchOrders();
    else if (activeTab === 'Traders') fetchInstrumentTrades();
  }, [activeTab, selectedInstrumentId, refreshTrigger, isPaused, isTabVisible]);

  useEffect(() => {
    getCurrentUser().then(res => { if (String(res?.code) === '0' && res.data?.id) setCurrentUserId(String(res.data.id)); }).catch(() => {});
  }, []);

  const orderColumns = [
    { key: 'orderId', label: 'Order Id' },
    { key: 'instrumentId', label: 'Instrument Id' },
    { key: 'clientOrderId', label: 'Client Order Id' },
    { key: 'status', label: 'Status' },
    { key: 'side', label: 'Side' },
    { key: 'type', label: 'Type' },
    { key: 'price', label: 'Price' },
    { key: 'quantity', label: 'Quantity' },
    { key: 'intent', label: 'Intent' },
    { key: 'filledQuantity', label: 'Filled Quantity' },
    { key: 'remainingQuantity', label: 'Remaining Quantity' },
    { key: 'avgFillPrice', label: 'Avg Fill Price' },
    { key: 'fee', label: 'Fee' },
    { key: 'rejectedReason', label: 'Rejected Reason' },
    { key: 'version', label: 'Version' },
    { key: 'createdAt', label: 'Created At' },
    { key: 'updatedAt', label: 'Updated At' },
    { key: 'submittedAt', label: 'Submitted At' },
    { key: 'filledAt', label: 'Filled At' },
    { key: 'cancelledAt', label: 'Cancelled At' }
  ];

  const columns = [
    { key: 'positionId', label: 'Position Id' },
    { key: 'instrumentId', label: 'Instrument Id' },
    { key: 'status', label: 'Status' },
    { key: 'side', label: 'Side' },
    { key: 'leverage', label: 'Leverage' },
    { key: 'margin', label: 'Margin' },
    { key: 'entryPrice', label: 'Entry Price' },
    { key: 'quantity', label: 'Quantity' },
    { key: 'closingReservedQuantity', label: 'Closing Reserved Quantity' },
    { key: 'markPrice', label: 'Mark Price' },
    { key: 'marginRatio', label: 'Margin Ratio' },
    { key: 'unrealizedPnl', label: 'Unrealized Pnl' },
    { key: 'cumRealizedPnl', label: 'Cum Realized Pnl' },
    { key: 'cumFee', label: 'Cum Fee' },
    { key: 'cumFundingFee', label: 'Cum Funding Fee' },
    { key: 'liquidationPrice', label: 'Liquidation Price' },
    { key: 'createdAt', label: 'Created At' },
    { key: 'updatedAt', label: 'Updated At' },
    { key: 'closedAt', label: 'Closed At' }
  ];

  const renderCellValue = (p: any, k: string, parentP?: any, eventContext?: any) => {
    const v = p[k];
    if (v === null) return 'Removed';
    
    let content = null;

    if (k === 'positionId') {
      content = <span className="text-slate-600 font-mono">{String(v)}</span>;
    } else if (k === 'instrumentId') {
      content = instrumentMap.get(String(v)) || String(v);
    } else if (k === 'status') {
      const status = String(v || '').toUpperCase();
      let colorClass = 'bg-slate-100 text-slate-600 border-slate-200';
      if (status === 'ACTIVE') colorClass = 'bg-emerald-50 text-emerald-600 border-emerald-200';
      if (status === 'CLOSED') colorClass = 'bg-rose-50 text-rose-600 border-rose-200';
      if (status === 'LIQUIDATING') colorClass = 'bg-amber-50 text-amber-600 border-amber-200';
      content = (
        <span className={`px-1.5 py-0.5 rounded-md border text-[9px] font-black uppercase tracking-tighter ${colorClass}`}>
          {status}
        </span>
      );
    } else if (k === 'side') {
      const side = String(v || '').toUpperCase();
      let colorClass = 'bg-slate-100 text-slate-600 border-slate-200';
      if (side === 'LONG' || side === 'BUY') colorClass = 'bg-emerald-50 text-emerald-600 border-emerald-200';
      if (side === 'SHORT' || side === 'SELL') colorClass = 'bg-rose-50 text-rose-600 border-rose-200';
      content = (
        <span className={`px-1.5 py-0.5 rounded-md border text-[9px] font-black uppercase tracking-tighter ${colorClass}`}>
          {side}
        </span>
      );
    } else if (['unrealizedPnl', 'cumRealizedPnl', 'cumFee', 'cumFundingFee'].includes(k)) {
      const n = Number(v);
      const color = n > 0 ? 'text-emerald-600 font-bold' : (n < 0 ? 'text-rose-600 font-bold' : 'text-slate-500');
      content = <span className={color}>{formatNumber(v)}</span>;
    } else if (['margin', 'marginRatio'].includes(k)) {
      content = <span className="text-sky-600 font-bold">{k === 'marginRatio' ? formatPercent(v) : formatNumber(v)}</span>;
    } else if (['entryPrice', 'quantity', 'markPrice', 'liquidationPrice'].includes(k)) {
      content = <span className="text-sky-600 font-bold">{formatNumber(v)}</span>;
      
      // Special logic for Entry Price in Position Events: Show implied trade price
      if (k === 'entryPrice' && eventContext && eventContext.prevState) {
          const curE = Number(v || 0);
          const prevE = Number(eventContext.prevState.entryPrice || 0);
          // Only show if Entry Price changed (and it's a trade event usually, but strict value change is safer)
          if (Math.abs(curE - prevE) > 0.000001) {
              const curQ = Number(p.quantity || 0);
              const prevQ = Number(eventContext.prevState.quantity || 0);
              const tradeQ = curQ - prevQ;
              
              if (Math.abs(tradeQ) > 0) {
                  // (NewE * NewQ - OldE * OldQ) / TradeQ
                  const tradeP = (curE * curQ - prevE * prevQ) / tradeQ;
                  
                  const tooltipContent = (
                      <div className="flex flex-col gap-1">
                          <div className="font-bold text-slate-800 border-b border-slate-900/10 pb-1 mb-1">Implied Trade Info</div>
                          <div className="grid grid-cols-[auto_1fr] gap-x-2 text-[10px] text-slate-600">
                              <span>Trade Price:</span> <span className="font-mono text-emerald-600">{formatNumber(tradeP)}</span>
                              <span>Trade Qty:</span> <span className="font-mono text-slate-700">{tradeQ > 0 ? '+' : ''}{formatNumber(tradeQ)}</span>
                          </div>
                          <div className="mt-1 bg-slate-900/5 p-1.5 rounded border border-slate-900/10 font-mono text-[9px] text-blue-700">
                              ({formatNumber(curE)}×{formatNumber(curQ)} - {formatNumber(prevE)}×{formatNumber(prevQ)}) / {formatNumber(tradeQ)}
                          </div>
                          <div className="text-[8px] text-slate-400 italic mt-0.5 text-right">*Derived from event state change</div>
                      </div>
                  );

                  content = (
                      <div className="flex items-center justify-end gap-1">
                          {content}
                          <Tooltip classNames={{ root: 'liquid-tooltip' }} title={tooltipContent}>
                              <div className="liquid-tooltip-trigger cursor-help text-[9px] text-slate-400 hover:text-blue-500 transition-colors px-0.5">∆</div>
                          </Tooltip>
                      </div>
                  );
              }
          }
      }
    } else if (k === 'closingReservedQuantity') {
      content = <span className="text-amber-600 font-bold">{formatNumber(v)}</span>;
    } else if (['createdAt', 'updatedAt', 'closedAt', 'submittedAt', 'filledAt', 'cancelledAt'].includes(k)) {
      if (!v) content = '-';
      else {
        const d = new Date(v);
        content = isNaN(d.getTime()) ? String(v) : `${d.getHours().toString().padStart(2,'0')}:${d.getMinutes().toString().padStart(2,'0')}:${d.getSeconds().toString().padStart(2,'0')}`;
      }
    } else {
      content = v ?? '-';
    }

    if (['margin', 'marginRatio', 'unrealizedPnl', 'liquidationPrice', 'cumFee', 'cumRealizedPnl'].includes(k)) {
       if (k === 'margin') {
           return <div className="flex items-center justify-end gap-1">{content}<span className="text-[9px] text-slate-400">+</span>{renderCalculatorTooltip(p, k, parentP, eventContext)}</div>;
       }
       return <div className="flex items-center justify-end gap-1">{content}{renderCalculatorTooltip(p, k, parentP, eventContext)}</div>;
    }
    
    return content;
  };

  const renderOrderCellValue = (o: any, k: string, tradesMap?: Record<string, TradeResponse[]>) => {
    const v = o[k];
    if (v === null) return 'Removed';
    
    let content = null;

    if (k === 'orderId') {
      content = <span className="text-slate-600 font-mono">{String(v)}</span>;
    } else if (k === 'instrumentId') {
      content = instrumentMap.get(String(v)) || String(v);
    } else if (k === 'status') {
      const status = String(v || '').toUpperCase();
      let colorClass = 'bg-slate-100 text-slate-600 border-slate-200';
      if (['FILLED', 'PARTIALLY_FILLED'].includes(status)) colorClass = 'bg-emerald-50 text-emerald-600 border-emerald-200';
      if (['CANCELLED', 'REJECTED'].includes(status)) colorClass = 'bg-rose-50 text-rose-600 border-rose-200';
      if (['NEW', 'PENDING_NEW'].includes(status)) colorClass = 'bg-sky-50 text-sky-600 border-sky-200';
      content = (
        <span className={`px-1.5 py-0.5 rounded-md border text-[9px] font-black uppercase tracking-tighter ${colorClass}`}>
          {status}
        </span>
      );
    } else if (k === 'side') {
      const side = String(v || '').toUpperCase();
      let colorClass = 'bg-slate-100 text-slate-600 border-slate-200';
      if (side === 'BUY' || side === 'LONG') colorClass = 'bg-emerald-50 text-emerald-600 border-emerald-200';
      if (side === 'SELL' || side === 'SHORT') colorClass = 'bg-rose-50 text-rose-600 border-rose-200';
      content = (
        <span className={`px-1.5 py-0.5 rounded-md border text-[9px] font-black uppercase tracking-tighter ${colorClass}`}>
          {side}
        </span>
      );
    } else if (k === 'type') {
      content = (
        <span className="px-1.5 py-0.5 rounded-md border border-violet-200 bg-violet-50 text-violet-600 text-[9px] font-black uppercase tracking-tighter">
          {String(v || '-')}
        </span>
      );
    } else if (k === 'intent') {
      content = (
        <span className="px-1.5 py-0.5 rounded-md border border-slate-200 bg-slate-100 text-slate-600 text-[9px] font-black uppercase tracking-tighter">
          {String(v || '-')}
        </span>
      );
    } else if (['price', 'quantity'].includes(k)) {
      content = <span className="text-sky-600 font-bold">{formatNumber(v)}</span>;
    } else if (['filledQuantity', 'remainingQuantity', 'avgFillPrice', 'fee'].includes(k)) {
      content = <span className="text-amber-600 font-bold">{formatNumber(v)}</span>;
    } else if (k === 'rejectedReason') {
      content = <span className="text-rose-600">{v || '-'}</span>;
    } else if (['createdAt', 'updatedAt', 'submittedAt', 'filledAt', 'cancelledAt'].includes(k)) {
      if (!v) content = '-';
      else {
        const d = new Date(v);
        content = isNaN(d.getTime()) ? String(v) : `${d.getHours().toString().padStart(2,'0')}:${d.getMinutes().toString().padStart(2,'0')}:${d.getSeconds().toString().padStart(2,'0')}`;
      }
    } else {
      content = v ?? '-';
    }

    // Add verification tooltip for Avg Fill Price and Fee using trade data
    if ((k === 'avgFillPrice' || k === 'fee') && tradesMap && tradesMap[o.orderId]) {
      const trades = tradesMap[o.orderId] || [];
      if (trades.length > 0) {
        let tooltipContent = null;
        
        if (k === 'avgFillPrice') {
          let totalVal = 0;
          let totalQty = 0;
          trades.forEach(t => {
            const p = Number(t.price || 0);
            const q = Number(t.quantity || 0);
            totalVal += p * q;
            totalQty += q;
          });
          const calcAvg = totalQty === 0 ? 0 : totalVal / totalQty;
          
          tooltipContent = (
            <div className="flex flex-col gap-1">
              <div className="font-bold text-slate-800 border-b border-slate-900/10 pb-1 mb-1">Implied Avg Price</div>
              <div className="text-[9px] text-slate-600 mb-1">From {trades.length} fills:</div>
              <div className="bg-slate-900/5 p-1.5 rounded border border-slate-900/10 font-mono text-[9px] text-blue-700">
                <div>Σ(Price × Qty) / Σ(Qty)</div>
                <div className="mt-1 font-bold">= {formatNumber(calcAvg)}</div>
              </div>
              <div className="text-[8px] text-slate-400 italic mt-0.5 text-right">*Implied from loaded fills</div>
            </div>
          );
        } else if (k === 'fee') {
          let totalFee = 0;
          trades.forEach(t => {
            const isTaker = t.takerUserId === Number(currentUserId);
            const fee = isTaker ? Number(t.takerFee) : Number(t.makerFee);
            totalFee += fee;
          });
          
          tooltipContent = (
            <div className="flex flex-col gap-1">
              <div className="font-bold text-slate-800 border-b border-slate-900/10 pb-1 mb-1">Implied Fee</div>
              <div className="text-[9px] text-slate-600 mb-1">Sum of {trades.length} fills:</div>
              <div className="bg-slate-900/5 p-1.5 rounded border border-slate-900/10 font-mono text-[9px] text-blue-700">
                <div>Σ(Trade Fee)</div>
                <div className="mt-1 font-bold">= {formatNumber(totalFee)}</div>
              </div>
              <div className="text-[8px] text-slate-400 italic mt-0.5 text-right">*Implied from loaded fills</div>
            </div>
          );
        }

        return (
          <div className="flex items-center justify-end gap-1">
            {content}
            <Tooltip classNames={{ root: 'liquid-tooltip' }} title={tooltipContent}>
              <div className="liquid-tooltip-trigger cursor-help text-[9px] text-slate-400 hover:text-blue-500 transition-colors px-0.5">∆</div>
            </Tooltip>
          </div>
        );
      }
    }

    return content;
  };

  const parsePayload = (p?: string | null) => { if (!p) return null; try { return JSON.parse(p); } catch { return null; } };

  useEffect(() => {
    if (expandedPositionId) {
        // Always fetch latest events when expanded or refreshed
        setPositionEventsLoading(v => ({...v, [expandedPositionId]: true}));
        getPositionEvents(expandedPositionId).then(res => { 
            if(String(res?.code)==='0') {
                setPositionEvents(v => ({...v, [expandedPositionId]: res.data?.events || []}));
            }
        }).finally(() => setPositionEventsLoading(v => ({...v, [expandedPositionId]: false})));
    }
  }, [expandedPositionId, refreshTrigger]);

  useEffect(() => {
    if (expandedOrderId) {
        setOrderEventsLoading(v => ({...v, [expandedOrderId]: true}));
        getOrderEvents(expandedOrderId).then(res => { if(String(res?.code)==='0') setOrderEvents(v => ({...v, [expandedOrderId]: res.data?.events || []})); }).finally(() => setOrderEventsLoading(v => ({...v, [expandedOrderId]: false})));
        getTradesByOrderId(expandedOrderId).then(res => { if(String(res?.code)==='0') setOrderTrades(v => ({...v, [expandedOrderId]: res.data || []})); });
    }
  }, [expandedOrderId, refreshTrigger]);

  function togglePosition(id: number) {
    if (expandedPositionId === id) { setExpandedPositionId(null); return; }
    setExpandedPositionId(id);
    if (!positionEvents[id]) {
        setPositionEventsLoading(v => ({...v, [id]: true}));
        getPositionEvents(id).then(res => {
            if (String(res?.code) === '0') {
                setPositionEvents(v => ({...v, [id]: res.data?.events || []}));
            }
        }).finally(() => setPositionEventsLoading(v => ({...v, [id]: false})));
    }
  }

  function toggleOrder(id: number) {
    if (expandedOrderId === id) { setExpandedOrderId(null); return; }
    setExpandedOrderId(id);
    if (!orderEvents[id]) {
        setOrderEventsLoading(v => ({...v, [id]: true}));
        getOrderEvents(id).then(res => {
            if (String(res?.code) === '0') {
                setOrderEvents(v => ({...v, [id]: res.data?.events || []}));
            }
        }).finally(() => setOrderEventsLoading(v => ({...v, [id]: false})));
        getTradesByOrderId(id).then(res => { if(String(res?.code)==='0') setOrderTrades(v => ({...v, [id]: res.data || []})); });
    }
  }

  const openTradeDetail = async (orderId: number, tradeId: number, event: React.MouseEvent) => {
    const rect = event.currentTarget.getBoundingClientRect();
    setTradeDetailAnchor({ top: rect.top, left: rect.right + 8 });
    setTradeDetailLoading(true);
    try {
      const cachedTrades = orderTrades[orderId] || [];
      if (cachedTrades.length === 0) {
        const res = await getTradesByOrderId(orderId);
        if (String(res?.code) === '0') {
          const trades = res.data || [];
          setOrderTrades(v => ({...v, [orderId]: trades}));
          const match = trades.find((t: TradeResponse) => Number(t.tradeId) === Number(tradeId)) || null;
          if (!match) {
            message.warning('Trade not found');
          }
          setTradeDetail(match);
          return;
        }
      } else {
        const match = cachedTrades.find(t => Number(t.tradeId) === Number(tradeId)) || null;
        if (!match) {
          message.warning('Trade not found');
        }
        setTradeDetail(match);
      }
    } catch {
      message.error('Failed to load trade');
    } finally {
      setTradeDetailLoading(false);
    }
  };

  const openTradeDetailByInstrument = async (instrumentId: number, tradeId: string, event: React.MouseEvent) => {
    const rect = event.currentTarget.getBoundingClientRect();
    setTradeDetailAnchor({ top: rect.top, left: rect.right + 8 });
    setTradeDetailLoading(true);
    try {
      const res = await getTradesByInstrument(instrumentId);
      if (String(res?.code) === '0') {
        const trades = res.data || [];
        const match = trades.find((t: TradeResponse) => String(t.tradeId) === tradeId) || null;
        if (!match) {
          message.warning('Trade not found');
        }
        setTradeDetail(match);
      }
    } catch {
      message.error('Failed to load trade');
    } finally {
      setTradeDetailLoading(false);
    }
  };

  return (
    <div className="flex h-full flex-col overflow-hidden">
      <div id="positions-tabs-bar" className="flex items-center justify-between px-4 py-2 border-b border-white/10 bg-white/5">
        <div className="inline-flex items-center gap-1 rounded-full border border-white/20 bg-white/5 p-1">
          {['Positions', 'Orders', 'Traders'].map((tab) => (
            <button key={tab} onClick={() => setActiveTab(tab)} className={`px-3 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider transition-all ${tab === activeTab ? 'bg-white text-slate-800 shadow-sm' : 'text-slate-500 hover:text-slate-700 hover:bg-white/20'}`}>
              {tab}
            </button>
          ))}
        </div>
      </div>

      <div className="flex-1 min-h-0 overflow-auto p-4">
        {activeTab === 'Positions' && (
          <div className="min-w-max">
            <table className="w-full text-xs text-right text-slate-600 border-separate border-spacing-x-0">
                <thead>
                  <tr className="text-[10px] uppercase text-slate-400 border-b border-white/10">
                    <th className="py-2 px-2"></th>
                    {columns.map(c => (
                      <th key={c.key} className="py-2 px-2 font-semibold">
                        <div className="flex items-center justify-end gap-1">
                          {c.label}
                          {['entryPrice', 'margin', 'marginRatio', 'unrealizedPnl', 'cumRealizedPnl', 'liquidationPrice'].includes(c.key) && (
                            <Tooltip
                              classNames={{ root: 'liquid-tooltip' }}
                              title={
                                c.key === 'entryPrice' ? (
                                  <div className="flex flex-col gap-2 p-1">
                                    <div className="font-bold text-slate-800">Avg Entry Price</div>
                                    <div className="text-[10px] text-slate-600">
                                      Weighted average of all fills.
                                    </div>
                                    <div className="mt-1 bg-slate-900/5 p-1.5 rounded border border-slate-900/10 font-mono text-[10px] text-blue-700">
                                      Σ(Fill Price × Fill Qty) / Total Qty
                                    </div>
                                  </div>
                                ) :
                                c.key === 'margin' ? (
                                  <div className="flex flex-col gap-2 p-1">
                                    <div className="font-bold text-slate-800">Margin</div>
                                    <div className="text-[10px] text-slate-600">
                                      Initial margin based on entry notional.
                                    </div>
                                    <div className="mt-1 bg-slate-900/5 p-1.5 rounded border border-slate-900/10 font-mono text-[10px] text-blue-700">
                                      Entry × Qty × Contract Size × IMR
                                    </div>
                                    <div className="text-[9px] text-slate-500 font-medium italic">
                                      IMR = Initial Margin Rate
                                    </div>
                                  </div>
                                ) :
                                c.key === 'marginRatio' ? (
                                  <div className="flex flex-col gap-2 p-1">
                                    <div className="font-bold text-slate-800">Margin Ratio</div>
                                    <div className="text-[10px] text-slate-600">
                                      Current position equity relative to notional value.
                                    </div>
                                    <div className="mt-1 bg-slate-900/5 p-1.5 rounded border border-slate-900/10 font-mono text-[10px] text-blue-700">
                                      (Margin + Unrealized PnL) / Notional
                                    </div>
                                    <div className="text-[9px] text-slate-500 font-medium italic">
                                      Notional = Mark Price × Qty × Contract Size
                                    </div>
                                  </div>
                                ) :
                                c.key === 'unrealizedPnl' ? (
                                  <div className="flex flex-col gap-2 p-1">
                                    <div className="font-bold text-slate-800">Unrealized PnL</div>
                                    <div className="text-[10px] text-slate-600">
                                      Floating profit/loss based on Mark Price.
                                    </div>
                                    <div className="flex flex-col gap-1 mt-1 bg-slate-900/5 p-1.5 rounded border border-slate-900/10">
                                      <div className="flex justify-between gap-3 text-[10px]">
                                        <span className="text-emerald-700 font-bold">Long</span>
                                        <span className="font-mono text-slate-700">(Mark - Entry) × Qty × Contract Size</span>
                                      </div>
                                      <div className="flex justify-between gap-3 text-[10px] border-t border-slate-900/5 pt-1">
                                        <span className="text-rose-700 font-bold">Short</span>
                                        <span className="font-mono text-slate-700">(Entry - Mark) × Qty × Contract Size</span>
                                      </div>
                                    </div>
                                  </div>
                                ) :
                                c.key === 'cumRealizedPnl' ? (
                                  <div className="flex flex-col gap-2 p-1">
                                    <div className="font-bold text-slate-800">Cum Realized PnL</div>
                                    <div className="text-[10px] text-slate-600">
                                      Total realized profit/loss including fees.
                                    </div>
                                    <div className="mt-1 bg-slate-900/5 p-1.5 rounded border border-slate-900/10 font-mono text-[10px] text-blue-700">
                                      Σ(Close PnL) - Σ(Fees)
                                    </div>
                                    <div className="flex items-start gap-1.5 mt-1 p-1.5 bg-amber-500/10 border border-amber-600/20 rounded text-[9px] text-amber-800">
                                      <span className="text-amber-600 font-bold">!</span>
                                      <span className="font-medium">Fees are deducted immediately upon trade execution.</span>
                                    </div>
                                  </div>
                                ) :
                                c.key === 'liquidationPrice' ? (
                                  <div className="flex flex-col gap-2 p-1">
                                    <div className="font-bold text-slate-800">Liquidation Price</div>
                                    <div className="text-[10px] text-slate-600">
                                      Price at which position hits Maintenance Margin.
                                    </div>
                                    <div className="flex flex-col gap-1 mt-1 bg-slate-900/5 p-1.5 rounded border border-slate-900/10">
                                      <div className="flex justify-between gap-3 text-[10px]">
                                        <span className="text-emerald-700 font-bold">Long</span>
                                        <span className="font-mono text-slate-700">(Entry - Margin/Qty) / (1 - MMR)</span>
                                      </div>
                                      <div className="flex justify-between gap-3 text-[10px] border-t border-slate-900/5 pt-1">
                                        <span className="text-rose-700 font-bold">Short</span>
                                        <span className="font-mono text-slate-700">(Entry + Margin/Qty) / (1 + MMR)</span>
                                      </div>
                                    </div>
                                    <div className="text-[9px] text-slate-500 font-medium italic">
                                      MMR = Maintenance Margin Rate
                                    </div>
                                  </div>
                                ) : ''
                              }
                            >
                              <div className="liquid-tooltip-trigger w-3 h-3 rounded-full bg-slate-100 flex items-center justify-center text-[9px] text-slate-400 cursor-help border border-slate-200">?</div>
                            </Tooltip>
                          )}
                        </div>
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-white/5">
                  {positions.length === 0 ? <tr><td colSpan={columns.length+1} className="py-8 text-center text-slate-400">No positions</td></tr> : positions.map(p => (
                    <Fragment key={p.positionId}>
                      <tr className="hover:bg-white/10 transition-colors">
                        <td className="py-2 px-2"><button onClick={() => togglePosition(p.positionId)} className="w-4 h-4 rounded border border-white/30 text-[10px]">{expandedPositionId === p.positionId ? 'v' : '>'}</button></td>
                        {columns.map(c => {
                          const changed = positionChangedFieldsRef.current.get(String(p.positionId))?.has(c.key);
                          return <td key={c.key} className={`py-2 px-2 font-mono ${changed ? 'bg-rose-100/50 text-rose-900' : ''}`}>{renderCellValue(p, c.key)}</td>
                        })}
                      </tr>
                      {expandedPositionId === p.positionId && (
                        <tr>
                          <td colSpan={columns.length + 1} className="p-2">
                            <div className="bg-slate-50/30 rounded-xl p-3 border border-white/20 overflow-x-auto">
                              <div className="text-[9px] uppercase font-bold text-slate-400 mb-2 text-left">Position Events</div>
                              <table className="w-full text-[10px] text-left border-separate border-spacing-x-0">
                                <thead>
                                    <tr className="text-slate-500">
                                      <th className="py-1 px-2 whitespace-nowrap font-bold">Seq</th>
                                      <th className="py-1 px-2 whitespace-nowrap font-bold">Event</th>
                                      <th className="py-1 px-2 whitespace-nowrap font-bold">Trade Id</th>
                                      {columns.map((c, i) => (
                                        <th key={c.key} className={`py-1 px-2 whitespace-nowrap font-bold text-slate-600 bg-yellow-200/50 ${i === 0 ? 'rounded-l-md' : ''} ${i === columns.length - 1 ? 'rounded-r-md' : ''}`}>{c.label}</th>
                                      ))}
                                      <th className="py-1 px-2 whitespace-nowrap font-bold">Time</th>
                                    </tr>
                                </thead>
                                <tbody>
                                  {(() => {
                                    const rawEvents = positionEvents[p.positionId] || [];
                                    const sortedEvents = [...rawEvents].sort((a, b) => Number(a.sequenceNumber) - Number(b.sequenceNumber));
                                    
                                    let accumulatedState: any = {};
                                    const processedEvents = sortedEvents.map(e => {
                                      const prevState = { ...accumulatedState };
                                      const payload = parsePayload(e.payload) || {};
                                      accumulatedState = { ...accumulatedState, ...payload };
                                      return { ...e, fullState: { ...accumulatedState }, prevState, deltaPayload: payload };
                                    });

                                    return processedEvents.reverse().map(e => {
                                      const refType = String(e.referenceType || '').toUpperCase();
                                      const rawRefId = String(e.referenceId || '');
                                      const tradeId = rawRefId ? rawRefId.split(':')[0] : '';
                                      return (
                                        <tr key={e.eventId} className="hover:bg-white/5">
                                          <td className="py-1 px-2 font-mono text-slate-400">{e.sequenceNumber}</td>
                                          <td className="py-1 px-2">
                                            <span className="px-1.5 py-0.5 rounded-md border border-slate-200 bg-slate-100 text-slate-600 text-[9px] font-black uppercase tracking-tighter">
                                              {e.eventType}
                                            </span>
                                          </td>
                                          <td className="py-1 px-2 font-mono">
                                            {refType === 'TRADE' && tradeId ? (
                                              <button
                                                className="text-blue-600 hover:text-blue-800 underline"
                                                onClick={(event) => openTradeDetailByInstrument(p.instrumentId, tradeId, event)}
                                              >
                                                {rawRefId}
                                              </button>
                                            ) : (
                                              '-'
                                            )}
                                          </td>
                                          {columns.map((c, i) => {
                                            const val = e.deltaPayload[c.key];
                                            const hasValue = val !== undefined && val !== null;
                                            return (
                                              <td key={c.key} className={`py-1 px-2 font-mono bg-yellow-200/50 ${hasValue ? 'text-slate-900 font-bold' : 'text-slate-300'} ${i === 0 ? 'rounded-l-md' : ''} ${i === columns.length - 1 ? 'rounded-r-md' : ''}`}>
                                                {hasValue ? renderCellValue(e.fullState, c.key, p, e) : '-'}
                                              </td>
                                            );
                                          })}
                                          <td className="py-1 px-2 text-slate-400 whitespace-nowrap">{formatPayloadValue('occurredAt', e.occurredAt)}</td>
                                        </tr>
                                      );
                                    });
                                  })()}
                                </tbody>
                              </table>
                            </div>
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  ))}
                </tbody>
              </table>
            </div>
          )}

        {activeTab === 'Orders' && (
          <div className="liquid-tooltip-trigger min-w-max">
            <table className="w-full text-xs text-right text-slate-600 border-separate border-spacing-x-0">
                <thead>
                  <tr className="text-[10px] uppercase text-slate-400 border-b border-white/10">
                    <th className="py-2 px-2"></th>
                    {orderColumns.map(c => <th key={c.key} className="py-2 px-2 font-semibold">{c.label}</th>)}
                  </tr>
                </thead>
                <tbody className="divide-y divide-white/5">
                  {orders.length === 0 ? <tr><td colSpan={orderColumns.length+1} className="py-8 text-center text-slate-400">No orders</td></tr> : orders.map(o => (
                    <Fragment key={o.orderId}>
                      <tr className="hover:bg-white/10 transition-colors">
                        <td className="py-2 px-2"><button onClick={() => toggleOrder(o.orderId)} className="w-4 h-4 rounded border border-white/30 text-[10px]">{expandedOrderId === o.orderId ? 'v' : '>'}</button></td>
                        {orderColumns.map(c => {
                          const changed = orderChangedFieldsRef.current.get(String(o.orderId))?.has(c.key);
                          return <td key={c.key} className={`py-2 px-2 font-mono ${changed ? 'bg-rose-100/50 text-rose-900' : ''}`}>{renderOrderCellValue(o, c.key, orderTrades)}</td>
                        })}
                      </tr>
                      {expandedOrderId === o.orderId && (
                        <tr>
                          <td colSpan={orderColumns.length + 1} className="p-2">
                            <div className="space-y-3">
                              <div className="bg-slate-50/30 rounded-xl p-3 border border-white/20 overflow-x-auto">
                                <div className="text-[9px] uppercase font-bold text-slate-400 mb-2 text-left">Order Events</div>
                                <table className="w-full text-[10px] text-left border-separate border-spacing-x-0">
                                  <thead>
                                    <tr className="text-slate-500">
                                      <th className="py-1 px-2 whitespace-nowrap font-bold">Seq</th>
                                      <th className="py-1 px-2 whitespace-nowrap font-bold">Event</th>
                                      <th className="py-1 px-2 whitespace-nowrap font-bold">Trade Id</th>
                                      {orderColumns.map((c, i) => (
                                        <th key={c.key} className={`py-1 px-2 whitespace-nowrap font-bold text-slate-600 bg-yellow-200/50 ${i === 0 ? 'rounded-l-md' : ''} ${i === orderColumns.length - 1 ? 'rounded-r-md' : ''}`}>{c.label}</th>
                                      ))}
                                      <th className="py-1 px-2 whitespace-nowrap font-bold">Time</th>
                                    </tr>
                                  </thead>
                                  <tbody>
                                    {orderEvents[o.orderId]?.map(e => {
                                      const payload = parsePayload(e.payload) || {};
                                      const isTradeRef = String(e.referenceType || '').toUpperCase() === 'TRADE';
                                      const tradeId = e.referenceId;
                                      return (
                                        <tr key={e.eventId} className="hover:bg-white/5">
                                          <td className="py-1 px-2 font-mono text-slate-400">{e.sequenceNumber}</td>
                                          <td className="py-1 px-2">
                                            <span className="px-1.5 py-0.5 rounded-md border border-slate-200 bg-slate-100 text-slate-600 text-[9px] font-black uppercase tracking-tighter">
                                              {e.eventType}
                                            </span>
                                          </td>
                                          <td className="py-1 px-2 font-mono">
                                            {isTradeRef && tradeId ? (
                                              <button
                                                className="text-blue-600 hover:text-blue-800 underline"
                                                onClick={(event) => openTradeDetail(o.orderId, Number(tradeId), event)}
                                              >
                                                {tradeId}
                                              </button>
                                            ) : (
                                              '-'
                                            )}
                                          </td>
                                          {orderColumns.map((c, i) => {
                                            const val = payload[c.key];
                                            const hasValue = val !== undefined && val !== null;
                                            return (
                                              <td key={c.key} className={`py-1 px-2 font-mono bg-yellow-200/50 ${hasValue ? 'text-slate-900 font-bold' : 'text-slate-300'} ${i === 0 ? 'rounded-l-md' : ''} ${i === orderColumns.length - 1 ? 'rounded-r-md' : ''}`}>
                                                {hasValue ? renderOrderCellValue(payload, c.key) : '-'}
                                              </td>
                                            );
                                          })}
                                          <td className="py-1 px-2 text-slate-400 whitespace-nowrap">{formatPayloadValue('occurredAt', e.occurredAt)}</td>
                                        </tr>
                                      );
                                    })}
                                  </tbody>
                                </table>
                              </div>
                              <div className="bg-slate-50/30 rounded-xl p-3 border border-white/20">
                                <div className="text-[9px] uppercase font-bold text-slate-400 mb-2 text-left">Fills / Trades</div>
                                <table className="w-full text-[10px] text-left">
                                  <thead>
                                    <tr className="text-slate-400 border-b border-white/10">
                                      <th className="py-1">Trade ID</th>
                                      <th className="py-1">Price</th>
                                      <th className="py-1">Qty</th>
                                      <th className="py-1">Fee</th>
                                      <th className="py-1">Time</th>
                                    </tr>
                                  </thead>
                                  <tbody>
                                    {orderTrades[o.orderId]?.map(t => (
                                      <tr key={t.tradeId} className="border-b border-white/5">
                                        <td className="py-1 font-mono">{t.tradeId}</td>
                                        <td className="py-1 font-mono">{formatNumber(t.price)}</td>
                                        <td className="py-1 font-mono">{formatNumber(t.quantity)}</td>
                                        <td className="py-1 font-mono text-rose-500">
                                          {formatNumber(String(t.counterpartyOrderId) === String(o.orderId) ? t.takerFee : t.makerFee)}
                                        </td>
                                        <td className="py-1 text-slate-400">{formatPayloadValue('executedAt', t.executedAt)}</td>
                                      </tr>
                                    ))}
                                  </tbody>
                                </table>
                              </div>
                            </div>
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  ))}
                </tbody>
              </table>
            </div>
          )}

        {activeTab === 'Traders' && (
          <div className="min-w-max">
            {(() => {
              const hasMyTaker = instrumentTrades.some(t => t.takerUserId === Number(currentUserId));
              const hasMyMaker = instrumentTrades.some(t => t.makerUserId === Number(currentUserId));
              const takerHeaderBg = hasMyTaker ? 'bg-yellow-200/50' : '';
              const makerHeaderBg = hasMyMaker ? 'bg-yellow-200/50' : '';
              
              return (
                <table className="w-full text-xs text-right text-slate-600 border-separate border-spacing-x-0">
                  <thead>
                    <tr className="text-[10px] uppercase text-slate-400 border-b border-white/10">
                      <th className="py-2 px-2">Trade Id</th>
                      <th className="py-2 px-2">Instrument</th>
                      <th className="py-2 px-2">Type</th>
                      <th className="py-2 px-2">Price</th>
                      <th className="py-2 px-2">Qty</th>
                      <th className="py-2 px-2">Value</th>

                      <th className={`py-2 px-2 ${takerHeaderBg} ${hasMyTaker ? 'rounded-tl-md' : ''}`}>Taker User</th>
                      <th className={`py-2 px-2 ${takerHeaderBg}`}>Taker Order</th>
                      <th className={`py-2 px-2 ${takerHeaderBg}`}>Taker Side</th>
                      <th className={`py-2 px-2 ${takerHeaderBg}`}>Taker Intent</th>
                      <th className={`py-2 px-2 ${takerHeaderBg}`}>Taker Fee</th>
                      <th className={`py-2 px-2 ${takerHeaderBg}`}>Taker Ord Qty</th>
                      <th className={`py-2 px-2 ${takerHeaderBg} ${hasMyTaker ? 'rounded-tr-md' : ''}`}>Taker Filled</th>

                      <th className={`py-2 px-2 ${makerHeaderBg} ${hasMyMaker ? 'rounded-tl-md' : ''}`}>Maker User</th>
                      <th className={`py-2 px-2 ${makerHeaderBg}`}>Maker Order</th>
                      <th className={`py-2 px-2 ${makerHeaderBg}`}>Maker Side</th>
                      <th className={`py-2 px-2 ${makerHeaderBg}`}>Maker Intent</th>
                      <th className={`py-2 px-2 ${makerHeaderBg}`}>Maker Fee</th>
                      <th className={`py-2 px-2 ${makerHeaderBg}`}>Maker Ord Qty</th>
                      <th className={`py-2 px-2 ${makerHeaderBg} ${hasMyMaker ? 'rounded-tr-md' : ''}`}>Maker Filled</th>

                      <th className="py-2 px-2">Executed</th>
                      <th className="py-2 px-2">Created</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-white/5">
                    {instrumentTrades.length === 0 ? <tr><td colSpan={22} className="py-8 text-center text-slate-400">No trades</td></tr> : instrumentTrades.map(t => {
                      const isTaker = t.takerUserId === Number(currentUserId);
                      const isMaker = t.makerUserId === Number(currentUserId);
                      const takerBg = isTaker ? 'bg-yellow-200/50' : '';
                      const makerBg = isMaker ? 'bg-yellow-200/50' : '';
                      
                      return (
                        <tr key={t.tradeId} className="hover:bg-white/10 transition-colors">
                          <td className="py-2 px-2 font-mono text-slate-500">{t.tradeId}</td>
                          <td className="py-2 px-2 font-mono text-slate-500">{instrumentMap.get(String(t.instrumentId)) || t.instrumentId}</td>
                          <td className="py-2 px-2">
                            <span className="px-1.5 py-0.5 rounded-md border border-violet-200 bg-violet-50 text-violet-600 text-[9px] font-black uppercase tracking-tighter">
                              {t.tradeType || '-'}
                            </span>
                          </td>
                          <td className="py-2 px-2 font-mono font-bold text-sky-600">{formatNumber(t.price)}</td>
                          <td className="py-2 px-2 font-mono font-bold text-sky-600">{formatNumber(t.quantity)}</td>
                          <td className="py-2 px-2 font-mono font-bold text-sky-600">{formatNumber(t.totalValue)}</td>

                          <td className={`py-2 px-2 font-mono ${takerBg} text-slate-600`}>{t.takerUserId}</td>
                          <td className={`py-2 px-2 font-mono ${takerBg} text-slate-600`}>{t.counterpartyOrderId}</td>
                          <td className={`py-2 px-2 ${takerBg}`}>
                              <span className={`px-1.5 py-0.5 rounded-md border text-[9px] font-black uppercase tracking-tighter ${t.counterpartyOrderSide === 'BUY' ? 'bg-emerald-50 text-emerald-600 border-emerald-200' : 'bg-rose-50 text-rose-600 border-rose-200'}`}>
                                {t.counterpartyOrderSide}
                              </span>
                          </td>
                          <td className={`py-2 px-2 ${takerBg}`}>
                            <span className="px-1.5 py-0.5 rounded-md border border-slate-200 bg-slate-100 text-slate-600 text-[9px] font-black uppercase tracking-tighter">
                              {t.takerIntent || '-'}
                            </span>
                          </td>
                          <td className={`py-2 px-2 font-mono ${takerBg} ${isTaker ? 'text-amber-600 font-bold' : 'text-slate-500'}`}>{formatNumber(t.takerFee)}</td>
                          <td className={`py-2 px-2 font-mono ${takerBg} text-slate-500`}>{formatNumber(t.counterpartyOrderQuantity)}</td>
                          <td className={`py-2 px-2 font-mono ${takerBg} text-slate-500`}>{formatNumber(t.counterpartyOrderFilledQuantity)}</td>

                          <td className={`py-2 px-2 font-mono ${makerBg} text-slate-600`}>{t.makerUserId}</td>
                          <td className={`py-2 px-2 font-mono ${makerBg} text-slate-600`}>{t.orderId}</td>
                          <td className={`py-2 px-2 ${makerBg}`}>
                              <span className={`px-1.5 py-0.5 rounded-md border text-[9px] font-black uppercase tracking-tighter ${t.orderSide === 'BUY' ? 'bg-emerald-50 text-emerald-600 border-emerald-200' : 'bg-rose-50 text-rose-600 border-rose-200'}`}>
                                {t.orderSide}
                              </span>
                          </td>
                          <td className={`py-2 px-2 ${makerBg}`}>
                            <span className="px-1.5 py-0.5 rounded-md border border-slate-200 bg-slate-100 text-slate-600 text-[9px] font-black uppercase tracking-tighter">
                              {t.makerIntent || '-'}
                            </span>
                          </td>
                          <td className={`py-2 px-2 font-mono ${makerBg} ${isMaker ? 'text-amber-600 font-bold' : 'text-slate-500'}`}>{formatNumber(t.makerFee)}</td>
                          <td className={`py-2 px-2 font-mono ${makerBg} text-slate-500`}>{formatNumber(t.orderQuantity)}</td>
                          <td className={`py-2 px-2 font-mono ${makerBg} text-slate-500`}>{formatNumber(t.orderFilledQuantity)}</td>

                          <td className="py-2 px-2 font-mono text-slate-400">{formatPayloadValue('executedAt', t.executedAt)}</td>
                          <td className="py-2 px-2 font-mono text-slate-400">{formatPayloadValue('createdAt', t.createdAt)}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              );
            })()}
          </div>
        )}
      </div>
      {tradeDetail && (
        <div className="fixed inset-0 z-50 bg-slate-900/30" onClick={() => setTradeDetail(null)}>
          <div
            className="absolute w-[360px] max-w-[90vw] -translate-y-full rounded-xl border border-slate-200 bg-white p-3 text-[11px] text-slate-700 shadow-xl"
            style={{ top: tradeDetailAnchor?.top ?? 16, left: tradeDetailAnchor?.left ?? 16 }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="mb-2 flex items-center justify-between">
              <div className="text-[10px] uppercase font-bold text-slate-500">Trade Detail</div>
              <button className="text-slate-400 hover:text-slate-600" onClick={() => setTradeDetail(null)}>x</button>
            </div>
            {tradeDetailLoading ? (
              <div className="py-6 text-center text-slate-400">Loading...</div>
            ) : (
              <div className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1">
                <span className="text-slate-400">Trade Id</span><span className="font-mono">{tradeDetail.tradeId}</span>
                <span className="text-slate-400">Instrument</span><span>{instrumentMap.get(String(tradeDetail.instrumentId)) || tradeDetail.instrumentId}</span>
                <span className="text-slate-400">Maker Order ID</span>
                <span className={`font-mono ${String(tradeDetail.makerUserId) === String(currentUserId) ? 'bg-amber-100/70 rounded-sm' : ''}`}>
                  {tradeDetail.orderId}
                </span>
                <span className="text-slate-400">Taker Orde ID</span>
                <span className={`font-mono ${String(tradeDetail.takerUserId) === String(currentUserId) ? 'bg-amber-100/70 rounded-sm' : ''}`}>
                  {tradeDetail.counterpartyOrderId}
                </span>
                <span className="text-slate-400">Price</span><span className="font-mono text-sky-600 font-bold">{formatNumber(tradeDetail.price)}</span>
                <span className="text-slate-400">Quantity</span><span className="font-mono text-sky-600 font-bold">{formatNumber(tradeDetail.quantity)}</span>
                <span className="text-slate-400">Maker Fee</span>
                <span className={`font-mono ${String(tradeDetail.makerUserId) === String(currentUserId) ? 'bg-amber-100/70 rounded-sm' : ''}`}>
                  {formatNumber(tradeDetail.makerFee)}
                </span>
                <span className="text-slate-400">Taker Fee</span>
                <span className={`font-mono ${String(tradeDetail.takerUserId) === String(currentUserId) ? 'bg-amber-100/70 rounded-sm' : ''}`}>
                  {formatNumber(tradeDetail.takerFee)}
                </span>
                <span className="text-slate-400">Executed At</span><span className="font-mono">{formatPayloadValue('executedAt', tradeDetail.executedAt)}</span>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
