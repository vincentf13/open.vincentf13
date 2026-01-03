import { Fragment, useEffect, useMemo, useRef, useState } from 'react';
import { message } from 'antd';

import type { InstrumentSummary } from '../../api/instrument';
import { getOrders, type OrderResponse } from '../../api/order';
import { getPositions, type PositionResponse } from '../../api/position';
import { getTradesByInstrument, getTradesByOrderId, type TradeResponse } from '../../api/trade';
import { getCurrentUser } from '../../api/user';

type PositionsProps = {
  instruments: InstrumentSummary[];
  selectedInstrumentId: string | null;
  refreshTrigger?: number;
};

const formatNumber = (value: string | number | null | undefined) => {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  const numeric = Number(value);
  if (Number.isNaN(numeric)) {
    return String(value);
  }
  return numeric.toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 6,
  });
};

const formatPercent = (value: string | number | null | undefined) => {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  const numeric = Number(value);
  if (Number.isNaN(numeric)) {
    return String(value);
  }
  return `${(numeric * 100).toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 4,
  })}%`;
};

const formatDateTime = (value: string | number | null | undefined) => {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const seconds = String(date.getSeconds()).padStart(2, '0');
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
};

export default function Positions({ instruments, selectedInstrumentId, refreshTrigger }: PositionsProps) {
  const [activeTab, setActiveTab] = useState('Positions');
  const [positions, setPositions] = useState<PositionResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [ordersLoading, setOrdersLoading] = useState(false);
  const [expandedOrderId, setExpandedOrderId] = useState<number | null>(null);
  const [orderTrades, setOrderTrades] = useState<Record<string, TradeResponse[]>>({});
  const [tradesLoading, setTradesLoading] = useState<Record<string, boolean>>({});
  const [instrumentTrades, setInstrumentTrades] = useState<TradeResponse[]>([]);
  const [instrumentTradesLoading, setInstrumentTradesLoading] = useState(false);
  const [currentUserId, setCurrentUserId] = useState<string | null>(null);
  const previousOrdersRef = useRef<Map<string, OrderResponse>>(new Map());
  const currentOrdersRef = useRef<OrderResponse[]>([]);

  const instrumentMap = useMemo(() => {
    return new Map(
      instruments.map((item) => [String(item.instrumentId), item.name || item.symbol || String(item.instrumentId)])
    );
  }, [instruments]);
  const instrumentContractSizeMap = useMemo(() => {
    return new Map(
      instruments.map((item) => {
        const rawSize = Number(item.contractSize);
        const size = Number.isFinite(rawSize) && rawSize > 0 ? rawSize : 1;
        return [String(item.instrumentId), size];
      })
    );
  }, [instruments]);
  const instrumentTakerFeeRateMap = useMemo(() => {
    return new Map(
      instruments.map((item) => {
        const rawRate = Number(item.takerFeeRate);
        const rate = Number.isFinite(rawRate) && rawRate >= 0 ? rawRate : 0;
        return [String(item.instrumentId), rate];
      })
    );
  }, [instruments]);

  const fetchPositions = async () => {
    setLoading(true);
    try {
      const response = await getPositions(selectedInstrumentId || undefined);
      if (String(response?.code) === '0') {
        setPositions(Array.isArray(response?.data) ? response.data : []);
      } else {
        setPositions([]);
        if (response?.message) {
          message.error(response.message);
        }
      }
    } catch (error: any) {
      setPositions([]);
      if (error?.response?.data?.message) {
        message.error(error.response.data.message);
      }
    } finally {
      setLoading(false);
    }
  };

  const fetchOrders = async () => {
    setOrdersLoading(true);
    try {
      const response = await getOrders(selectedInstrumentId || undefined);
      if (String(response?.code) === '0') {
        const nextOrders = Array.isArray(response?.data) ? response.data : [];
        previousOrdersRef.current = new Map(
          currentOrdersRef.current.map((order) => [String(order.orderId), order])
        );
        currentOrdersRef.current = nextOrders;
        setOrders(nextOrders);
      } else {
        setOrders([]);
        if (response?.message) {
          message.error(response.message);
        }
      }
    } catch (error: any) {
      setOrders([]);
      if (error?.response?.data?.message) {
        message.error(error.response.data.message);
      }
    } finally {
      setOrdersLoading(false);
    }
  };

  const fetchTrades = async (orderId: number) => {
    setTradesLoading((prev) => ({ ...prev, [orderId]: true }));
    try {
      const response = await getTradesByOrderId(orderId);
      if (String(response?.code) === '0') {
        setOrderTrades((prev) => ({
          ...prev,
          [orderId]: Array.isArray(response?.data) ? response.data : [],
        }));
      } else {
        setOrderTrades((prev) => ({ ...prev, [orderId]: [] }));
        if (response?.message) {
          message.error(response.message);
        }
      }
    } catch (error: any) {
      setOrderTrades((prev) => ({ ...prev, [orderId]: [] }));
      if (error?.response?.data?.message) {
        message.error(error.response.data.message);
      }
    } finally {
      setTradesLoading((prev) => ({ ...prev, [orderId]: false }));
    }
  };

  const fetchInstrumentTrades = async () => {
    if (!selectedInstrumentId) {
      setInstrumentTrades([]);
      return;
    }
    setInstrumentTradesLoading(true);
    try {
      const response = await getTradesByInstrument(selectedInstrumentId);
      if (String(response?.code) === '0') {
        setInstrumentTrades(Array.isArray(response?.data) ? response.data : []);
      } else {
        setInstrumentTrades([]);
        if (response?.message) {
          message.error(response.message);
        }
      }
    } catch (error: any) {
      setInstrumentTrades([]);
      if (error?.response?.data?.message) {
        message.error(error.response.data.message);
      }
    } finally {
      setInstrumentTradesLoading(false);
    }
  };

  useEffect(() => {
    fetchPositions();
  }, []);

  useEffect(() => {
    let cancelled = false;
    const loadCurrentUser = async () => {
      try {
        const response = await getCurrentUser();
        if (cancelled) {
          return;
        }
        if (String(response?.code) === '0' && response?.data?.id != null) {
          setCurrentUserId(String(response.data.id));
        } else {
          setCurrentUserId(null);
        }
      } catch {
        if (!cancelled) {
          setCurrentUserId(null);
        }
      }
    };
    loadCurrentUser();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (activeTab === 'Positions') {
      fetchPositions();
    }
    if (activeTab === 'Orders') {
      fetchOrders();
    }
    if (activeTab === 'Traders') {
      fetchInstrumentTrades();
    }
  }, [activeTab, selectedInstrumentId, refreshTrigger]);

  const resolveContractSize = (instrumentId: string | number | null | undefined) => {
    if (instrumentId === null || instrumentId === undefined) {
      return 1;
    }
    const size = instrumentContractSizeMap.get(String(instrumentId));
    return size ?? 1;
  };
  const resolveTakerFeeRate = (instrumentId: string | number | null | undefined) => {
    if (instrumentId === null || instrumentId === undefined) {
      return 0;
    }
    const rate = instrumentTakerFeeRateMap.get(String(instrumentId));
    return rate ?? 0;
  };
  const ordersReservedSummary = useMemo(() => {
    return orders.reduce(
      (acc, order) => {
        const remainingQuantityValue = Number(order.remainingQuantity);
        const orderPriceValue = Number(order.price);
        if (!Number.isFinite(remainingQuantityValue) || !Number.isFinite(orderPriceValue)) {
          return acc;
        }
        const contractSize = resolveContractSize(order.instrumentId);
        const reservedValue = remainingQuantityValue * orderPriceValue * contractSize;
        if (Number.isFinite(reservedValue)) {
          acc.reservedTotal += reservedValue;
          const takerFeeRate = resolveTakerFeeRate(order.instrumentId);
          if (Number.isFinite(takerFeeRate)) {
            acc.reservedFeeTotal += reservedValue * takerFeeRate;
          }
        }
        return acc;
      },
      { reservedTotal: 0, reservedFeeTotal: 0 }
    );
  }, [orders, instrumentContractSizeMap, instrumentTakerFeeRateMap]);

  const orderColumns = [
    { key: 'instrumentId', label: 'Instrument' },
    { key: 'side', label: 'Side' },
    { key: 'type', label: 'Order Type' },
    { key: 'price', label: 'Price' },
    { key: 'quantity', label: 'Quantity' },
    { key: 'intent', label: 'Position Intent' },
    { key: 'status', label: 'Status' },
    { key: 'filledQuantity', label: 'Filled Quantity' },
    { key: 'remainingQuantity', label: 'Remaining Quantity' },
    { key: 'avgFillPrice', label: 'Average Fill Price' },
    { key: 'fee', label: 'Fee' },
    { key: 'rejectedReason', label: 'Rejected Reason' },
    { key: 'createdAt', label: 'Created Time' },
    { key: 'updatedAt', label: 'Updated Time' },
    { key: 'submittedAt', label: 'Submitted Time' },
    { key: 'filledAt', label: 'Filled Time' },
    { key: 'cancelledAt', label: 'Cancelled Time' },
  ];
  const orderHighlightKeys = new Set([
    'status',
    'filledQuantity',
    'remainingQuantity',
    'avgFillPrice',
    'fee',
    'rejectedReason',
  ]);
  const getOrderCompareValue = (order: OrderResponse, key: string) => {
    const value = (order as any)[key];
    return value === null || value === undefined ? '' : String(value);
  };
  const hasOrderFieldChanged = (order: OrderResponse, key: string) => {
    if (!orderHighlightKeys.has(key)) {
      return false;
    }
    const previousOrder = previousOrdersRef.current.get(String(order.orderId));
    if (!previousOrder) {
      return false;
    }
    return getOrderCompareValue(order, key) !== getOrderCompareValue(previousOrder, key);
  };

  const tradeColumns = [
    { key: 'tradeId', label: 'Trade ID' },
    { key: 'instrumentId', label: 'Instrument' },
    { key: 'makerUserId', label: 'Maker User ID' },
    { key: 'takerUserId', label: 'Taker User ID' },
    { key: 'orderId', label: 'Order ID' },
    { key: 'counterpartyOrderId', label: 'Counterparty Order ID' },
    { key: 'orderSide', label: 'Order Side' },
    { key: 'counterpartyOrderSide', label: 'Counterparty Order Side' },
    { key: 'makerIntent', label: 'Maker Intent' },
    { key: 'takerIntent', label: 'Taker Intent' },
    { key: 'tradeType', label: 'Trade Type' },
    { key: 'price', label: 'Price' },
    { key: 'quantity', label: 'Quantity' },
    { key: 'totalValue', label: 'Total Value' },
    { key: 'makerFee', label: 'Maker Fee' },
    { key: 'takerFee', label: 'Taker Fee' },
    { key: 'executedAt', label: 'Executed Time' },
    { key: 'createdAt', label: 'Created Time' },
  ];

  const makerHighlightKeys = new Set([
    'makerUserId',
    'orderId',
    'orderSide',
    'makerIntent',
    'makerFee',
  ]);
  const takerHighlightKeys = new Set([
    'takerUserId',
    'counterpartyOrderId',
    'counterpartyOrderSide',
    'takerIntent',
    'takerFee',
  ]);

  const renderOrderCellValue = (order: OrderResponse, key: string) => {
    if (key === 'instrumentId') {
      return instrumentMap.get(String(order.instrumentId)) || String(order.instrumentId);
    }
    if (
      ['price', 'quantity', 'filledQuantity', 'remainingQuantity', 'avgFillPrice', 'fee'].includes(key)
    ) {
      return formatNumber((order as any)[key]);
    }
    if (['createdAt', 'updatedAt', 'submittedAt', 'filledAt', 'cancelledAt'].includes(key)) {
      return formatDateTime((order as any)[key]);
    }
    return (order as any)[key] ?? '-';
  };

  const renderTradeCellValue = (trade: TradeResponse, key: string) => {
    if (key === 'instrumentId') {
      return instrumentMap.get(String(trade.instrumentId)) || String(trade.instrumentId);
    }
    if (['price', 'quantity', 'totalValue', 'makerFee', 'takerFee'].includes(key)) {
      return formatNumber((trade as any)[key]);
    }
    if (['executedAt', 'createdAt'].includes(key)) {
      return formatDateTime((trade as any)[key]);
    }
    return (trade as any)[key] ?? '-';
  };

  const columns = [
    { key: 'instrumentId', label: 'Instrument' },
    { key: 'side', label: 'Side' },
    { key: 'status', label: 'Status' },
    { key: 'entryPrice', label: 'Entry Price' },
    { key: 'quantity', label: 'Quantity' },
    { key: 'closingReservedQuantity', label: 'Closing Reserved' },
    { key: 'markPrice', label: 'Mark Price' },
    { key: 'liquidationPrice', label: 'Liquidation Price' },
    { key: 'unrealizedPnl', label: 'Unrealized PnL' },
    { key: 'cumRealizedPnl', label: 'Realized PnL' },
    { key: 'cumFee', label: 'Fee' },
    { key: 'cumFundingFee', label: 'Funding Fee' },
    { key: 'leverage', label: 'Leverage' },
    { key: 'margin', label: 'Margin' },
    { key: 'marginRatio', label: 'Margin Ratio' },
    { key: 'createdAt', label: 'Created Time' },
    { key: 'updatedAt', label: 'Updated Time' },
    { key: 'closedAt', label: 'Closed Time' },
  ];

  const renderCellValue = (position: PositionResponse, key: string) => {
    if (key === 'instrumentId') {
      return instrumentMap.get(String(position.instrumentId)) || String(position.instrumentId);
    }
    if (key === 'unrealizedPnl') {
      return formatNumber(position.unrealizedPnl);
    }
    if (
      ['margin', 'entryPrice', 'quantity', 'closingReservedQuantity', 'markPrice', 'marginRatio', 'cumRealizedPnl', 'cumFee',
        'cumFundingFee', 'liquidationPrice'].includes(key)
    ) {
      if (key === 'marginRatio') {
        return formatPercent((position as any)[key]);
      }
      return formatNumber((position as any)[key]);
    }
    if (['createdAt', 'updatedAt', 'closedAt'].includes(key)) {
      return formatDateTime((position as any)[key]);
    }
    return (position as any)[key] ?? '-';
  };

  return (
    <div className="flex flex-col overflow-hidden bg-white/5">
      <div className="flex items-center justify-between px-4 py-3 border-b border-white/20">
        <div className="flex items-center gap-4 text-xs font-semibold">
          {['Positions', 'Orders', 'Traders'].map((tab) => (
            <button
              key={tab}
              onClick={() => {
                setActiveTab(tab);
                if (tab === 'Positions') {
                  fetchPositions();
                }
                if (tab === 'Orders') {
                  fetchOrders();
                }
                if (tab === 'Traders') {
                  fetchInstrumentTrades();
                }
              }}
              className={`uppercase tracking-wider transition-colors ${tab === activeTab ? 'text-slate-700' : 'text-slate-400 hover:text-slate-600'}`}
            >
              {tab}
            </button>
          ))}
        </div>
      </div>

      <div className="p-4 overflow-x-auto">
        {activeTab === 'Positions' && (
          <table className="min-w-[2600px] w-full text-xs text-right text-slate-600">
            <thead>
              <tr className="text-[10px] uppercase text-slate-400 tracking-wider border-b border-white/20">
                {columns.map((column) => (
                  <th key={column.key} className="py-2 px-2 font-semibold text-right whitespace-nowrap">
                    {column.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-white/10">
              {positions.length === 0 && (
                <tr>
                  <td className="py-6 text-center text-slate-400 text-xs" colSpan={columns.length}>
                    {loading ? 'Loading...' : 'No positions'}
                  </td>
                </tr>
              )}
              {positions.map((position) => {
                return (
                  <tr key={position.positionId} className="hover:bg-white/20 transition-colors">
                    {columns.map((column) => {
                      const value = renderCellValue(position, column.key);
                      const isPnl = column.key === 'unrealizedPnl' || column.key === 'cumRealizedPnl';
                      const isFee = column.key === 'cumFee' || column.key === 'cumFundingFee';
                      const isTag = column.key === 'side' || column.key === 'status';
                      const sideValue = column.key === 'side' ? String(value).toUpperCase() : '';
                      const pnlValue = isPnl ? Number((position as any)[column.key] || 0) : 0;
                      const pnlClass = isPnl ? (pnlValue >= 0 ? 'text-emerald-600' : 'text-rose-500') : '';
                      const feeClass = isFee ? 'text-rose-500' : '';
                      const cellClass = feeClass || pnlClass;
                      const tagBaseClass =
                        'inline-flex items-center justify-center text-[10px] uppercase font-semibold tracking-wider rounded-md px-1.5 py-0.5 border';
                      const sideTagClass =
                        sideValue === 'LONG'
                          ? 'text-emerald-700 bg-emerald-400/20 border-emerald-400/40'
                          : sideValue === 'SHORT'
                            ? 'text-rose-700 bg-rose-400/20 border-rose-400/40'
                            : 'text-slate-600 bg-white/40 border-white/50';
                      const statusTagClass = 'text-slate-600 bg-white/40 border-white/50';
                      const cellContent = isTag ? (
                        <span className={`${tagBaseClass} ${column.key === 'side' ? sideTagClass : statusTagClass}`}>
                          {String(value)}
                        </span>
                      ) : (
                        value
                      );
                      return (
                        <td key={column.key} className={`py-3 px-2 font-mono whitespace-nowrap text-right ${cellClass}`}>
                          {cellContent}
                        </td>
                      );
                    })}
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
        {activeTab === 'Orders' && (
          <div>
            <table className="min-w-[2400px] w-full text-xs text-right text-slate-600">
              <thead>
                <tr className="text-[10px] uppercase text-slate-400 tracking-wider border-b border-white/20">
                  <th className="py-2 px-2 font-semibold text-right whitespace-nowrap"></th>
                  {orderColumns.map((column) => (
                    <th key={column.key} className="py-2 px-2 font-semibold text-right whitespace-nowrap">
                      {column.label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-white/10">
                {orders.length === 0 && (
                  <tr>
                    <td className="py-6 text-center text-slate-400 text-xs" colSpan={orderColumns.length + 1}>
                      {ordersLoading ? 'Loading...' : 'No orders'}
                    </td>
                  </tr>
                )}
                {orders.map((order) => {
                  const isExpanded = expandedOrderId === order.orderId;
                  const tradesForOrder = orderTrades[order.orderId] || [];
                  const tradeSummary = tradesForOrder.reduce(
                    (acc, trade) => {
                      const isMakerForOrder =
                        trade.orderId != null &&
                        order.orderId != null &&
                        String(trade.orderId) === String(order.orderId);
                      const isTakerForOrder =
                        trade.counterpartyOrderId != null &&
                        order.orderId != null &&
                        String(trade.counterpartyOrderId) === String(order.orderId);
                      if (!isMakerForOrder && !isTakerForOrder) {
                        return acc;
                      }
                      const priceValue = Number(trade.price);
                      const quantityValue = Number(trade.quantity);
                      const contractSize = resolveContractSize(trade.instrumentId ?? order.instrumentId);
                      if (Number.isFinite(priceValue) && Number.isFinite(quantityValue)) {
                        acc.fillTotal += priceValue * quantityValue * contractSize;
                        acc.quantityTotal += quantityValue * contractSize;
                      }
                      const feeValue = Number(isMakerForOrder ? trade.makerFee : trade.takerFee);
                      if (Number.isFinite(feeValue)) {
                        acc.feeTotal += feeValue;
                      }
                      acc.matchedCount += 1;
                      return acc;
                    },
                    { fillTotal: 0, feeTotal: 0, matchedCount: 0, quantityTotal: 0 }
                  );
                  const hasTradeSummary = tradeSummary.matchedCount > 0;
                  const averageFillPrice =
                    tradeSummary.quantityTotal > 0
                      ? tradeSummary.fillTotal / tradeSummary.quantityTotal
                      : null;
                  const remainingQuantityValue = Number(order.remainingQuantity);
                  const orderPriceValue = Number(order.price);
                  const orderContractSize = resolveContractSize(order.instrumentId);
                  const orderTakerFeeRate = resolveTakerFeeRate(order.instrumentId);
                  const reservedValue =
                    Number.isFinite(remainingQuantityValue) && Number.isFinite(orderPriceValue)
                      ? remainingQuantityValue * orderPriceValue * orderContractSize
                      : null;
                  const reservedFeeValue =
                    reservedValue !== null && Number.isFinite(orderTakerFeeRate)
                      ? reservedValue * orderTakerFeeRate
                      : null;
                  return (
                    <Fragment key={order.orderId}>
                      <tr className="hover:bg-white/20 transition-colors">
                        <td className="py-3 px-2 text-right">
                          <button
                            type="button"
                            className="h-5 w-5 rounded-md border border-white/50 bg-white/30 text-[10px] text-slate-600 hover:bg-white/50"
                            onClick={() => {
                              const next = isExpanded ? null : order.orderId;
                              setExpandedOrderId(next);
                              if (!isExpanded) {
                                fetchTrades(order.orderId);
                              }
                            }}
                          >
                            {isExpanded ? 'v' : '>'}
                          </button>
                        </td>
                      {orderColumns.map((column) => {
                        const value = renderOrderCellValue(order, column.key);
                        const isTag = column.key === 'side' || column.key === 'type' || column.key === 'status';
                        const tagClass =
                          'inline-flex items-center justify-center text-[10px] uppercase text-slate-600 font-semibold tracking-wider bg-white/40 border border-white/50 rounded-md px-1.5 py-0.5';
                        const cellClass = hasOrderFieldChanged(order, column.key) ? 'bg-rose-100/70 text-rose-900' : '';
                        return (
                          <td key={column.key} className={`py-3 px-2 font-mono whitespace-nowrap text-right ${cellClass}`}>
                            {isTag ? <span className={tagClass}>{String(value)}</span> : value}
                          </td>
                        );
                      })}
                      </tr>
                      {isExpanded && (
                        <tr>
                          <td className="py-3 px-2" colSpan={orderColumns.length + 1}>
                            <div className="rounded-xl border border-white/40 bg-white/20 p-3">
                              <div className="mb-2 w-full text-left text-[10px] uppercase text-slate-500 font-semibold tracking-wider">
                                Trades
                              </div>
                              <div className="overflow-x-auto">
                                <table className="min-w-[2000px] w-full text-[11px] text-right text-slate-600">
                                  <thead>
                                    <tr className="text-[10px] uppercase text-slate-400 tracking-wider border-b border-white/20">
                                      {tradeColumns.map((column) => (
                                        <th key={column.key} className="py-2 px-2 font-semibold text-right whitespace-nowrap">
                                          {column.label}
                                        </th>
                                      ))}
                                    </tr>
                                  </thead>
                                  <tbody className="divide-y divide-white/10">
                                    {tradesForOrder.length === 0 && (
                                      <tr>
                                        <td className="py-4 text-center text-slate-400 text-xs" colSpan={tradeColumns.length}>
                                          {tradesLoading[order.orderId] ? 'Loading...' : 'No trades'}
                                        </td>
                                      </tr>
                                    )}
                                    {tradesForOrder.map((trade) => (
                                      <tr key={trade.tradeId}>
                                        {tradeColumns.map((column) => {
                                          const isTakerForOrder =
                                            trade.counterpartyOrderId != null &&
                                            order.orderId != null &&
                                            String(trade.counterpartyOrderId) === String(order.orderId);
                                          const isMakerForOrder =
                                            trade.orderId != null &&
                                            order.orderId != null &&
                                            String(trade.orderId) === String(order.orderId);
                                          const shouldHighlight =
                                            (isTakerForOrder && takerHighlightKeys.has(column.key)) ||
                                            (isMakerForOrder && makerHighlightKeys.has(column.key));
                                          const cellClass = shouldHighlight ? 'bg-amber-100/70 text-amber-900' : '';
                                          return (
                                            <td
                                              key={column.key}
                                              className={`py-2 px-2 font-mono whitespace-nowrap text-right ${cellClass}`}
                                            >
                                              {renderTradeCellValue(trade, column.key)}
                                            </td>
                                          );
                                        })}
                                      </tr>
                                    ))}
                                  </tbody>
                                </table>
                              </div>
                              <div className="mt-2 w-full text-[10px] text-slate-500 font-semibold text-left">
                                Total Fill Price: {hasTradeSummary ? formatNumber(tradeSummary.fillTotal) : '-'}
                                <br />
                                Average Fill Price: {hasTradeSummary ? formatNumber(averageFillPrice) : '-'}
                                <br />
                                Fee: {hasTradeSummary ? formatNumber(tradeSummary.feeTotal) : '-'}
                                <br />
                                Reserved: {reservedValue !== null ? formatNumber(reservedValue) : '-'}
                                <br />
                                Reserved Fee: {reservedFeeValue !== null ? formatNumber(reservedFeeValue) : '-'}
                              </div>
                            </div>
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  );
                })}
              </tbody>
            </table>
            <div className="mt-2 w-full text-[10px] text-slate-500 font-semibold text-left">
              Reserved: {orders.length ? formatNumber(ordersReservedSummary.reservedTotal) : '-'}
              <br />
              Reserved Fee: {orders.length ? formatNumber(ordersReservedSummary.reservedFeeTotal) : '-'}
            </div>
          </div>
        )}
        {activeTab === 'Traders' && (
          <table className="min-w-[2000px] w-full text-xs text-right text-slate-600">
            <thead>
              <tr className="text-[10px] uppercase text-slate-400 tracking-wider border-b border-white/20">
                {tradeColumns.map((column) => (
                  <th key={column.key} className="py-2 px-2 font-semibold text-right whitespace-nowrap">
                    {column.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-white/10">
              {instrumentTrades.length === 0 && (
                <tr>
                  <td className="py-6 text-center text-slate-400 text-xs" colSpan={tradeColumns.length}>
                    {instrumentTradesLoading ? 'Loading...' : 'No trades'}
                  </td>
                </tr>
              )}
              {instrumentTrades.map((trade) => (
                <tr key={trade.tradeId} className="hover:bg-white/20 transition-colors">
                  {tradeColumns.map((column) => {
                    const isMakerForUser =
                      currentUserId != null &&
                      trade.makerUserId != null &&
                      String(trade.makerUserId) === currentUserId;
                    const isTakerForUser =
                      currentUserId != null &&
                      trade.takerUserId != null &&
                      String(trade.takerUserId) === currentUserId;
                    const makerHighlight = isMakerForUser && makerHighlightKeys.has(column.key);
                    const takerHighlight = isTakerForUser && takerHighlightKeys.has(column.key);
                    const cellClass = makerHighlight
                      ? 'bg-rose-100/70 text-rose-900'
                      : takerHighlight
                        ? 'bg-amber-100/70 text-amber-900'
                        : '';
                    return (
                      <td key={column.key} className={`py-3 px-2 font-mono whitespace-nowrap text-right ${cellClass}`}>
                        {renderTradeCellValue(trade, column.key)}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
