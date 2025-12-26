import { useEffect, useMemo, useState } from 'react';
import { message } from 'antd';

import type { InstrumentSummary } from '../../api/instrument';
import { getOrders, type OrderResponse } from '../../api/order';
import { getPositions, type PositionResponse } from '../../api/position';

type PositionsProps = {
  instruments: InstrumentSummary[];
  selectedInstrumentId: string | null;
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

export default function Positions({ instruments, selectedInstrumentId }: PositionsProps) {
  const [activeTab, setActiveTab] = useState('Positions');
  const [positions, setPositions] = useState<PositionResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [ordersLoading, setOrdersLoading] = useState(false);

  const instrumentMap = useMemo(() => {
    return new Map(
      instruments.map((item) => [String(item.instrumentId), item.name || item.symbol || String(item.instrumentId)])
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
        setOrders(Array.isArray(response?.data) ? response.data : []);
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

  useEffect(() => {
    fetchPositions();
  }, []);

  useEffect(() => {
    if (activeTab === 'Positions') {
      fetchPositions();
    }
    if (activeTab === 'Orders') {
      fetchOrders();
    }
  }, [activeTab, selectedInstrumentId]);

  const orderColumns = [
    { key: 'instrumentId', label: 'Instrument' },
    { key: 'orderId', label: 'Order ID' },
    { key: 'clientOrderId', label: 'Client Order ID' },
    { key: 'side', label: 'Side' },
    { key: 'type', label: 'Order Type' },
    { key: 'price', label: 'Price' },
    { key: 'quantity', label: 'Quantity' },
    { key: 'intent', label: 'Position Intent' },
    { key: 'filledQuantity', label: 'Filled Quantity' },
    { key: 'remainingQuantity', label: 'Remaining Quantity' },
    { key: 'avgFillPrice', label: 'Average Fill Price' },
    { key: 'fee', label: 'Fee' },
    { key: 'status', label: 'Status' },
    { key: 'rejectedReason', label: 'Rejected Reason' },
    { key: 'version', label: 'Version' },
    { key: 'createdAt', label: 'Created Time' },
    { key: 'updatedAt', label: 'Updated Time' },
    { key: 'submittedAt', label: 'Submitted Time' },
    { key: 'filledAt', label: 'Filled Time' },
    { key: 'cancelledAt', label: 'Cancelled Time' },
  ];

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
          {['Positions', 'Orders', 'Trades'].map((tab) => (
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
          <table className="min-w-[2400px] w-full text-xs text-right text-slate-600">
            <thead>
              <tr className="text-[10px] uppercase text-slate-400 tracking-wider border-b border-white/20">
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
                  <td className="py-6 text-center text-slate-400 text-xs" colSpan={orderColumns.length}>
                    {ordersLoading ? 'Loading...' : 'No orders'}
                  </td>
                </tr>
              )}
              {orders.map((order) => (
                <tr key={order.orderId} className="hover:bg-white/20 transition-colors">
                  {orderColumns.map((column) => (
                    <td key={column.key} className="py-3 px-2 font-mono whitespace-nowrap text-right">
                      {renderOrderCellValue(order, column.key)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {activeTab === 'Trades' && (
          <div className="py-8 text-center text-xs text-slate-400">No trades</div>
        )}
      </div>
    </div>
  );
}
