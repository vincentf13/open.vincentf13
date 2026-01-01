
import { Dropdown } from 'antd';
import { useEffect, useState } from 'react';

import type { InstrumentSummary } from '../../api/instrument';
import { getMarkPrice, getTicker, type MarkPriceResponse, type TickerResponse } from '../../api/market';

type MarketStatsProps = {
  instruments: InstrumentSummary[];
  selectedInstrument: InstrumentSummary | null;
  onSelectInstrument: (instrument: InstrumentSummary) => void;
  refreshTrigger?: number;
};

export default function MarketStats({
  instruments,
  selectedInstrument,
  onSelectInstrument,
  refreshTrigger,
}: MarketStatsProps) {
    const currentName = selectedInstrument?.name || selectedInstrument?.symbol || 'BTC/USDT';
    const [dropdownOpen, setDropdownOpen] = useState(false);
    const [previewInstrumentId, setPreviewInstrumentId] = useState<string | null>(
        selectedInstrument?.instrumentId ?? null
    );
    const [ticker, setTicker] = useState<TickerResponse | null>(null);
    const [tickerLoading, setTickerLoading] = useState(false);
    const [showExtraStats, setShowExtraStats] = useState(false);
    const [markPrice, setMarkPrice] = useState<MarkPriceResponse | null>(null);
    const [markPriceLoading, setMarkPriceLoading] = useState(false);
    const instrumentTypeLabelMap: Record<string, string> = {
        SPOT: 'Spot',
        PERPETUAL: 'Perpetual',
        FUTURES: 'Futures',
        OPTION: 'Option',
    };
    const instrumentTypeKey = selectedInstrument?.instrumentType
        ? String(selectedInstrument.instrumentType).toUpperCase()
        : '';
    const instrumentTypeLabel = instrumentTypeLabelMap[instrumentTypeKey] || 'Instrument';
    const quoteAssetLabel = selectedInstrument?.quoteAsset
        ? String(selectedInstrument.quoteAsset).toUpperCase()
        : 'USDT';
    const baseAssetLabel = selectedInstrument?.baseAsset
        ? String(selectedInstrument.baseAsset).toUpperCase()
        : 'BTC';

    useEffect(() => {
        if (dropdownOpen) {
            setPreviewInstrumentId(selectedInstrument?.instrumentId ?? null);
        }
    }, [dropdownOpen, selectedInstrument]);

    const previewInstrument =
        instruments.find(item => String(item.instrumentId) === String(previewInstrumentId)) ||
        selectedInstrument ||
        instruments[0] ||
        null;
    const detailEntries = previewInstrument ? Object.entries(previewInstrument) : [];

    const formatDetailValue = (value: unknown) => {
        if (value === null || value === undefined) {
            return '-';
        }
        if (typeof value === 'object') {
            return JSON.stringify(value);
        }
        return String(value);
    };

    const formatTickerNumber = (value: string | number | null | undefined) => {
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

    const formatTickerPercent = (value: string | number | null | undefined) => {
        if (value === null || value === undefined || value === '') {
            return '-';
        }
        const numeric = Number(value);
        if (Number.isNaN(numeric)) {
            return String(value);
        }
        return `${(numeric * 100).toLocaleString(undefined, {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
        })}%`;
    };
    const changeClass = Number(ticker?.priceChange24h || 0) >= 0 ? 'text-emerald-600' : 'text-rose-500';
    const changePctClass = Number(ticker?.priceChangePct || 0) >= 0 ? 'text-emerald-600' : 'text-rose-500';
    const changeDirection = Number(ticker?.priceChangePct || 0) >= 0 ? '▲' : '▼';
    const markPriceDisplay = formatTickerNumber(markPrice?.markPrice);

    useEffect(() => {
        if (!selectedInstrument?.instrumentId) {
            setTicker(null);
            return;
        }
        let cancelled = false;
        const loadTicker = async () => {
            setTickerLoading(true);
            try {
                const response = await getTicker(selectedInstrument.instrumentId);
                if (cancelled) {
                    return;
                }
                if (String(response?.code) === '0') {
                    setTicker(response?.data || null);
                } else {
                    setTicker(null);
                }
            } catch (error) {
                if (!cancelled) {
                    setTicker(null);
                }
            } finally {
                if (!cancelled) {
                    setTickerLoading(false);
                }
            }
        };
        loadTicker();
        return () => {
            cancelled = true;
        };
    }, [selectedInstrument?.instrumentId, refreshTrigger]);

    useEffect(() => {
        if (!selectedInstrument?.instrumentId) {
            setMarkPrice(null);
            return;
        }
        let cancelled = false;
        const loadMarkPrice = async () => {
            setMarkPriceLoading(true);
            try {
                const response = await getMarkPrice(selectedInstrument.instrumentId);
                if (cancelled) {
                    return;
                }
                if (String(response?.code) === '0') {
                    setMarkPrice(response?.data || null);
                } else {
                    setMarkPrice(null);
                }
            } catch (error) {
                if (!cancelled) {
                    setMarkPrice(null);
                }
            } finally {
                if (!cancelled) {
                    setMarkPriceLoading(false);
                }
            }
        };
        loadMarkPrice();
        return () => {
            cancelled = true;
        };
    }, [selectedInstrument?.instrumentId, refreshTrigger]);

    return (
        <div className="p-4">
            <div className="grid grid-cols-1 lg:grid-cols-[auto,1fr] gap-4 items-start">
                <div className="flex flex-col gap-2">
                    <div className="flex items-center gap-2">
                        <Dropdown
                            trigger={['click']}
                            open={dropdownOpen}
                            onOpenChange={setDropdownOpen}
                            dropdownRender={() => (
                                <div className="flex w-[520px] rounded-xl border border-white/60 bg-white/90 shadow-lg backdrop-blur-sm overflow-hidden">
                                    <div className="w-[200px] border-r border-slate-200 max-h-64 overflow-auto">
                                        {instruments.length ? (
                                            instruments.map((instrument) => {
                                                const instrumentId = String(instrument.instrumentId);
                                                const isActive = instrumentId === String(selectedInstrument?.instrumentId);
                                                return (
                                                    <button
                                                        key={instrumentId}
                                                        type="button"
                                                        onMouseEnter={() => setPreviewInstrumentId(instrumentId)}
                                                        onClick={() => {
                                                            onSelectInstrument(instrument);
                                                            setDropdownOpen(false);
                                                        }}
                                                        className={`w-full px-3 py-2 text-left text-xs font-medium border-b border-slate-100 transition-colors ${
                                                            isActive ? 'bg-slate-100 text-slate-900' : 'text-slate-600 hover:bg-slate-50'
                                                        }`}
                                                    >
                                                        <div className="text-slate-800">{instrument.name || instrument.symbol || instrumentId}</div>
                                                        {instrument.symbol && instrument.name && (
                                                            <div className="text-[10px] text-slate-400">{instrument.symbol}</div>
                                                        )}
                                                    </button>
                                                );
                                            })
                                        ) : (
                                            <div className="px-3 py-2 text-xs text-slate-400">Loading</div>
                                        )}
                                    </div>
                                    <div className="flex-1 max-h-64 overflow-auto p-3">
                                        <div className="text-[11px] font-semibold text-slate-600 mb-2">Instrument Detail</div>
                                        {detailEntries.length ? (
                                            <div className="space-y-1">
                                                {detailEntries.map(([key, value]) => (
                                                    <div key={key} className="flex items-start justify-between gap-3">
                                                        <span className="text-[10px] uppercase text-slate-400 tracking-wider">{key}</span>
                                                        <span className="text-[11px] text-slate-700 font-mono text-right break-all">
                                                            {formatDetailValue(value)}
                                                        </span>
                                                    </div>
                                                ))}
                                            </div>
                                        ) : (
                                            <div className="text-xs text-slate-400">No data</div>
                                        )}
                                    </div>
                                </div>
                            )}
                        >
                            <button
                                type="button"
                                className="text-sm font-bold text-slate-800 inline-flex items-center gap-1 hover:text-slate-900"
                            >
                                {currentName}
                                <span className="text-xs text-slate-500">v</span>
                            </button>
                        </Dropdown>
                        <span className="text-[10px] uppercase text-slate-500 font-semibold tracking-wider">{instrumentTypeLabel}</span>
                        <span className="text-[10px] uppercase text-slate-600 font-semibold tracking-wider bg-white/40 border border-white/50 rounded-md px-1.5 py-0.5">{quoteAssetLabel}</span>
                    </div>
                    <div className="flex items-center gap-3">
                        <span className="text-2xl font-bold text-slate-800">
                            {markPriceLoading ? '...' : markPriceDisplay === '-' ? '-' : `$${markPriceDisplay}`}
                        </span>
                        <span
                            className={`text-sm font-medium px-2 py-0.5 rounded-lg border flex items-center gap-1 shadow-sm ${
                                Number(ticker?.priceChangePct || 0) >= 0
                                    ? 'text-emerald-600 bg-emerald-400/20 border-emerald-400/30'
                                    : 'text-rose-500 bg-rose-400/20 border-rose-400/30'
                            }`}
                        >
                            {changeDirection} {tickerLoading ? '...' : formatTickerPercent(ticker?.priceChangePct)}
                        </span>
                    </div>
                </div>

                <div className="grid grid-cols-2 sm:grid-cols-[repeat(4,minmax(0,1fr))_auto] gap-x-6 gap-y-3 text-xs text-slate-600 items-start">
                    <div className="flex items-center gap-2">
                        <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider">Mark</span>
                        <span className="font-mono font-medium text-slate-700">
                            {markPriceLoading ? '...' : formatTickerNumber(markPrice?.markPrice)}
                        </span>
                    </div>
                    <div className="flex items-center gap-2">
                        <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider">Index</span>
                        <span className="font-mono font-medium text-slate-700">
                            {markPriceLoading ? '...' : formatTickerNumber(markPrice?.markPrice)}
                        </span>
                    </div>
                    <div className="flex items-center gap-2">
                        <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider">Funding</span>
                        <span className="font-mono font-medium text-orange-500">0.0100%</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider">Next</span>
                        <span className="font-mono font-medium text-slate-600">01:52:10</span>
                    </div>
                    <div className="flex items-center gap-2 sm:col-start-1 sm:row-start-2">
                        <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider">24h High</span>
                        <span className="font-mono font-medium text-slate-700">
                            {tickerLoading ? '...' : formatTickerNumber(ticker?.high24h)}
                        </span>
                    </div>
                    <div className="flex items-center gap-2 sm:col-start-2 sm:row-start-2">
                        <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider">24h Low</span>
                        <span className="font-mono font-medium text-slate-700">
                            {tickerLoading ? '...' : formatTickerNumber(ticker?.low24h)}
                        </span>
                    </div>
                    <div className="flex items-center gap-2 sm:col-start-3 sm:row-start-2">
                        <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider">
                            24h Vol ({baseAssetLabel})
                        </span>
                        <span className="font-mono font-medium text-slate-700">
                            {tickerLoading ? '...' : formatTickerNumber(ticker?.volume24h)}
                        </span>
                    </div>
                    <div className="flex items-center gap-2 sm:col-start-4 sm:row-start-2">
                        <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider">
                            24h Turnover ({quoteAssetLabel})
                        </span>
                        <span className="font-mono font-medium text-slate-700">
                            {tickerLoading ? '...' : formatTickerNumber(ticker?.turnover24h)}
                        </span>
                    </div>
                    {showExtraStats && (
                        <div className="flex items-center gap-2 sm:col-start-1 sm:row-start-3">
                            <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider">24h Change</span>
                            <span className={`font-mono font-medium ${changeClass}`}>
                                {tickerLoading ? '...' : formatTickerNumber(ticker?.priceChange24h)}
                            </span>
                        </div>
                    )}
                    {showExtraStats && (
                        <div className="flex items-center gap-2 sm:col-start-2 sm:row-start-3">
                            <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider">24h Change %</span>
                            <span className={`font-mono font-medium ${changePctClass}`}>
                                {tickerLoading ? '...' : formatTickerPercent(ticker?.priceChangePct)}
                            </span>
                        </div>
                    )}
                    <div className="flex items-center justify-end sm:col-start-5 sm:row-start-2">
                        <button
                            type="button"
                            onClick={() => setShowExtraStats((prev) => !prev)}
                            className="inline-flex items-center gap-1 text-[10px] uppercase font-semibold tracking-wider text-slate-500 hover:text-slate-700"
                        >
                            {showExtraStats ? 'Less' : 'More'}
                            <span className="text-[9px]">{showExtraStats ? '˄' : '˅'}</span>
                        </button>
                    </div>
                </div>
            </div>
        </div>
    )
}
