
import { useCallback, useEffect, useMemo, useRef, useState, type MouseEvent, type WheelEvent as ReactWheelEvent } from 'react';

import { getKlines, type KlineResponse } from '../../api/market';

type ChartProps = {
  instrumentId: string | null;
  refreshTrigger?: number;
};

type DragState = {
  active: boolean;
  startX: number;
  startY: number;
  startOffset: number;
  startScale: number;
  count: number;
};

type SeriesPoint = {
  open: number;
  close: number;
  high: number;
  low: number;
  volume: number;
  turnover: number;
  tradeCount: number | null;
  takerBuyVolume: number | null;
  takerBuyTurnover: number | null;
  isClosed: boolean | null;
  bucketStart?: string;
  bucketEnd?: string;
};

  const periods = ['1m', '5m', '1h', '1d'] as const;
const CHART_WIDTH = 800;
const CHART_HEIGHT = 300;
const PRICE_HEIGHT = 220;
const VOLUME_TOP = 240;
const VOLUME_HEIGHT = 60;

const formatAxisNumber = (value: number) => {
  if (!Number.isFinite(value)) {
    return '-';
  }
  return value.toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
};

const formatVolumeNumber = (value: number) => {
  if (!Number.isFinite(value)) {
    return '-';
  }
  return value.toLocaleString(undefined, {
    maximumFractionDigits: 2,
  });
};

const formatCountNumber = (value: number) => {
  if (!Number.isFinite(value)) {
    return '-';
  }
  return value.toLocaleString(undefined, {
    maximumFractionDigits: 0,
  });
};

const formatOptionalNumber = (value: number | null | undefined, formatter: (value: number) => string) => {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return '-';
  }
  return formatter(value);
};

const formatOptionalBoolean = (value: boolean | null | undefined) => {
  if (value === null || value === undefined) {
    return '-';
  }
  return value ? 'true' : 'false';
};

const clamp = (value: number, min: number, max: number) => {
  return Math.min(max, Math.max(min, value));
};

const toNumber = (value: string | number | null | undefined) => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
};

const formatTimeLabel = (value: string | undefined, period: string) => {
  if (!value) {
    return '';
  }
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) {
    return '';
  }
  const pad2 = (num: number) => String(num).padStart(2, '0');
  const yyyy = date.getFullYear();
  const mm = pad2(date.getMonth() + 1);
  const dd = pad2(date.getDate());
  const hh = pad2(date.getHours());
  const mi = pad2(date.getMinutes());
  if (period === '1d') {
    return `${yyyy}-${mm}-${dd}`;
  }
  return `${hh}:${mi}`;
};

const formatDateTime = (value: string | undefined) => {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) {
    return '-';
  }
  const pad2 = (num: number) => String(num).padStart(2, '0');
  const yyyy = date.getFullYear();
  const mm = pad2(date.getMonth() + 1);
  const dd = pad2(date.getDate());
  const hh = pad2(date.getHours());
  const mi = pad2(date.getMinutes());
  const ss = pad2(date.getSeconds());
  return `${yyyy}-${mm}-${dd} ${hh}:${mi}:${ss}`;
};

export default function Chart({ instrumentId, refreshTrigger }: ChartProps) {
  const chartContainerRef = useRef<HTMLDivElement | null>(null);
  const dragStateRef = useRef<DragState | null>(null);
  const [period, setPeriod] = useState<(typeof periods)[number]>('1h');
  const [chartMode, setChartMode] = useState<'line' | 'candle'>('line');
  const [viewOffset, setViewOffset] = useState(0);
  const [priceScale, setPriceScale] = useState(1);
  const [isDragging, setIsDragging] = useState(false);
  const [klines, setKlines] = useState<KlineResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [crosshair, setCrosshair] = useState<{
    xRatio: number;
    yRatio: number;
    mode: 'price' | 'volume';
    value: number;
  } | null>(null);
  const [viewCount, setViewCount] = useState(120);

  useEffect(() => {
    if (!instrumentId) {
      setKlines([]);
      return;
    }
    let cancelled = false;
    const loadKlines = async () => {
      setLoading(true);
      try {
        const response = await getKlines(instrumentId, period, 200);
        if (cancelled) {
          return;
        }
        if (String(response?.code) === '0' && Array.isArray(response?.data)) {
          setKlines(response.data);
        } else {
          setKlines([]);
        }
      } catch (error) {
        if (!cancelled) {
          setKlines([]);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };
    loadKlines();
    return () => {
      cancelled = true;
    };
  }, [instrumentId, period, refreshTrigger]);

  const series = useMemo<SeriesPoint[]>(() => {
    if (!klines.length) {
      return [];
    }
    const sorted = [...klines].sort((a, b) => {
      const aTime = a.bucketStart ? new Date(a.bucketStart).getTime() : 0;
      const bTime = b.bucketStart ? new Date(b.bucketStart).getTime() : 0;
      return aTime - bTime;
    });
    return sorted
      .map((item) => {
        const open = toNumber(item.open);
        const high = toNumber(item.high);
        const low = toNumber(item.low);
        const close = toNumber(item.close);
        if (open === null || high === null || low === null || close === null) {
          return null;
        }
        return {
          open,
          close,
          high,
          low,
          volume: toNumber(item.volume) ?? 0,
          turnover: toNumber(item.turnover) ?? 0,
          tradeCount: toNumber(item.tradeCount),
          takerBuyVolume: toNumber(item.takerBuyVolume),
          takerBuyTurnover: toNumber(item.takerBuyTurnover),
          isClosed: item.isClosed ?? null,
          bucketStart: item.bucketStart,
          bucketEnd: item.bucketEnd,
        };
      })
      .filter((item): item is SeriesPoint => item !== null);
  }, [klines]);

  useEffect(() => {
    if (!series.length) {
      return;
    }
    setViewCount((prev) => {
      if (!prev || prev <= 0) {
        return Math.min(series.length, 120);
      }
      return Math.min(prev, series.length);
    });
  }, [series.length]);

  const visibleSeries = useMemo(() => {
    if (!series.length) {
      return [];
    }
    const count = Math.min(viewCount, series.length);
    const maxOffset = Math.max(0, series.length - count);
    const clampedOffset = Math.min(viewOffset, maxOffset);
    const start = Math.max(0, series.length - count - clampedOffset);
    return series.slice(start);
  }, [series, viewCount, viewOffset]);

  const chartMetrics = useMemo(() => {
    if (!visibleSeries.length) {
      return null;
    }
    const highs = visibleSeries.map((point) => point.high);
    const lows = visibleSeries.map((point) => point.low);
    const rawMax = Math.max(...highs);
    const rawMin = Math.min(...lows);
    const rawRange = rawMax - rawMin || 1;
    const scaledRange = rawRange * priceScale;
    const center = (rawMax + rawMin) / 2;
    const min = center - scaledRange / 2;
    const max = center + scaledRange / 2;
    return { min, max, range: scaledRange };
  }, [visibleSeries, priceScale]);

  const useLineChart = chartMode === 'line';

  const volumeMax = useMemo(() => {
    if (!visibleSeries.length) {
      return 0;
    }
    return Math.max(...visibleSeries.map((point) => point.volume || 0));
  }, [visibleSeries]);

  const timeGridXs = useMemo(() => {
    if (!visibleSeries.length) {
      return [];
    }
    const step = CHART_WIDTH / Math.max(visibleSeries.length, 1);
    return visibleSeries.map((_, index) => step * index + step / 2);
  }, [visibleSeries]);

  const linePaths = useMemo(() => {
    if (!chartMetrics || !visibleSeries.length) {
      return null;
    }
    const step = CHART_WIDTH / Math.max(visibleSeries.length, 1);
    const { min, range } = chartMetrics;
    const points = visibleSeries.map((point, index) => {
      const x = step * index + step / 2;
      const y = PRICE_HEIGHT - ((point.close - min) / range) * PRICE_HEIGHT;
      return { x, y };
    });
    const linePath = points
      .map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`)
      .join(' ');
    const areaPath = `${linePath} L ${points[points.length - 1].x} ${PRICE_HEIGHT} L ${points[0].x} ${PRICE_HEIGHT} Z`;
    return { linePath, areaPath };
  }, [chartMetrics, visibleSeries]);

  const handleMouseMove = (event: MouseEvent<HTMLDivElement>) => {
    if (dragStateRef.current?.active) {
      return;
    }
    if (!chartMetrics || !series.length) {
      setCrosshair(null);
      return;
    }
    const rect = event.currentTarget.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) {
      return;
    }
    const localX = clamp(event.clientX - rect.left, 0, rect.width);
    const localY = clamp(event.clientY - rect.top, 0, rect.height);
    const xRatio = localX / rect.width;
    const yRatio = localY / rect.height;
    const chartY = yRatio * CHART_HEIGHT;
    const clampedY = clamp(chartY, 0, PRICE_HEIGHT);
    const { min, range } = chartMetrics;
    const price = min + ((PRICE_HEIGHT - clampedY) / PRICE_HEIGHT) * range;
    const isVolumeArea = chartY >= VOLUME_TOP;
    const volumeRatio = clamp((chartY - VOLUME_TOP) / VOLUME_HEIGHT, 0, 1);
    const volumeValue = volumeMax > 0 ? volumeMax * (1 - volumeRatio) : 0;
    setCrosshair({
      xRatio,
      yRatio,
      mode: isVolumeArea ? 'volume' : 'price',
      value: isVolumeArea ? volumeValue : price,
    });
  };

  const handleMouseLeave = () => {
    if (dragStateRef.current?.active) {
      return;
    }
    setCrosshair(null);
  };

  const priceTicks = useMemo(() => {
    if (!chartMetrics) {
      return [];
    }
    const { min, max, range } = chartMetrics;
    const mid = (min + max) / 2;
    // 使用 5 個均勻分佈的刻度
    const values = [max, (max + mid) / 2, mid, (min + mid) / 2, min];
    return values.map((value) => {
      const y = PRICE_HEIGHT - ((value - min) / range) * PRICE_HEIGHT;
      return {
        value,
        y,
      };
    });
  }, [chartMetrics]);

  const volumeTicks = useMemo(() => {
    if (!volumeMax || !Number.isFinite(volumeMax)) {
      return [];
    }
    const values = [volumeMax, volumeMax / 2];
    return values.map((value) => {
      const ratio = volumeMax > 0 ? value / volumeMax : 0;
      const y = VOLUME_TOP + (1 - ratio) * VOLUME_HEIGHT;
      return {
        value,
        y,
      };
    });
  }, [volumeMax]);

  const timeTicks = useMemo(() => {
    if (!visibleSeries.length) {
      return [];
    }
    const size = visibleSeries.length;
    const step = CHART_WIDTH / Math.max(size, 1);
    const indices = [0, Math.floor((size - 1) / 3), Math.floor(((size - 1) * 2) / 3), size - 1];
    const unique = Array.from(new Set(indices)).filter((idx) => idx >= 0 && idx < size);
    return unique.map((idx) => {
      const label = formatTimeLabel(visibleSeries[idx]?.bucketStart, period);
      return {
        x: step * idx + step / 2,
        label,
      };
    });
  }, [visibleSeries, period]);

  const hoveredPoint = useMemo(() => {
    if (!crosshair || !visibleSeries.length) {
      return null;
    }
    const index = Math.min(visibleSeries.length - 1, Math.max(0, Math.floor(crosshair.xRatio * visibleSeries.length)));
    return {
      index,
      point: visibleSeries[index],
    };
  }, [crosshair, visibleSeries]);

  const applyWheelZoom = useCallback((deltaY: number) => {
    if (!series.length) {
      return;
    }
    const direction = deltaY > 0 ? 1 : -1;
    setViewCount((prev) => {
      const current = prev || series.length;
      const next =
        direction > 0 ? Math.round(current * 1.15) : Math.max(20, Math.round(current * 0.85));
      return Math.min(series.length, Math.max(20, next));
    });
    setPriceScale((prev) => {
      const factor = direction > 0 ? 1.08 : 0.92;
      return clamp(prev * factor, 0.2, 5);
    });
  }, [series.length]);

  const beginDrag = (event: MouseEvent<HTMLDivElement>) => {
    if (event.button !== 0 || !chartMetrics || !series.length) {
      return;
    }
    event.preventDefault();
    const count = Math.min(viewCount, series.length);
    dragStateRef.current = {
      active: true,
      startX: event.clientX,
      startY: event.clientY,
      startOffset: viewOffset,
      startScale: priceScale,
      count,
    };
    setIsDragging(true);
    setCrosshair(null);
  };

  const updateDrag = useCallback((event: MouseEvent) => {
    const state = dragStateRef.current;
    if (!state?.active) {
      return;
    }
    const deltaX = event.clientX - state.startX;
    const deltaY = event.clientY - state.startY;
    const step = CHART_WIDTH / Math.max(state.count, 1);
    const offsetDelta = Math.round(deltaX / step);
    const maxOffset = Math.max(0, series.length - state.count);
    const nextOffset = Math.min(maxOffset, Math.max(0, state.startOffset + offsetDelta));
    setViewOffset(nextOffset);
    const scaleFactor = clamp(1 + deltaY / PRICE_HEIGHT, 0.2, 5);
    setPriceScale(clamp(state.startScale * scaleFactor, 0.2, 5));
  }, [series.length]);

  const endDrag = useCallback(() => {
    if (dragStateRef.current?.active) {
      dragStateRef.current = null;
      setIsDragging(false);
    }
  }, []);

  const handleWheel = (event: ReactWheelEvent<HTMLDivElement>) => {
    event.preventDefault();
    event.stopPropagation();
    applyWheelZoom(event.deltaY);
  };

  useEffect(() => {
    const node = chartContainerRef.current;
    if (!node) {
      return;
    }
    const onWheel = (event: WheelEvent) => {
      event.preventDefault();
      event.stopPropagation();
      applyWheelZoom(event.deltaY);
    };
    node.addEventListener('wheel', onWheel, { passive: false });
    return () => {
      node.removeEventListener('wheel', onWheel);
    };
  }, [applyWheelZoom]);

  useEffect(() => {
    if (!isDragging) {
      return;
    }
    const handleMove = (event: MouseEvent) => {
      updateDrag(event);
    };
    const handleUp = () => {
      endDrag();
    };
    window.addEventListener('mousemove', handleMove);
    window.addEventListener('mouseup', handleUp);
    return () => {
      window.removeEventListener('mousemove', handleMove);
      window.removeEventListener('mouseup', handleUp);
    };
  }, [isDragging, updateDrag, endDrag]);

  useEffect(() => {
    setViewOffset(0);
    setPriceScale(1);
  }, [instrumentId, period]);

  useEffect(() => {
    if (!series.length) {
      setViewOffset(0);
      return;
    }
    setViewOffset((prev) => {
      const count = Math.min(viewCount, series.length);
      const maxOffset = Math.max(0, series.length - count);
      return Math.min(prev, maxOffset);
    });
  }, [series.length, viewCount]);

  return (
    <div className="flex flex-col h-full bg-white/5">
      <div className="relative flex items-center justify-end border-b border-white/20 px-4 py-3">
        <span className="absolute left-4 top-3 text-[10px] uppercase text-slate-400 font-bold tracking-wider">
          KLINE COUNTS {loading ? '...' : visibleSeries.length}
        </span>
        <div className="flex flex-col items-end gap-1">
          <div className="flex items-center gap-2">
            <div className="flex items-center gap-1 bg-white/30 rounded-lg p-1 border border-white/40 shadow-inner">
              {periods.map((t) => (
                <button
                  key={t}
                  onClick={() => setPeriod(t)}
                  className={`px-3 py-1 text-xs font-medium rounded-md transition-all ${t === period ? 'bg-white shadow-sm text-slate-800 font-semibold' : 'text-slate-500 hover:text-slate-700 hover:bg-white/40'}`}
                >
                  {t}
                </button>
              ))}
            </div>
            <div className="flex items-center gap-1 bg-white/20 rounded-lg p-1 border border-white/30">
              <button
                type="button"
                onClick={() => setChartMode('line')}
                className={`px-2 py-1 text-[10px] font-semibold uppercase tracking-wider rounded-md transition-all ${
                  chartMode === 'line'
                    ? 'bg-white text-slate-800 shadow-sm'
                    : 'text-slate-500 hover:text-slate-700 hover:bg-white/40'
                }`}
              >
                Line
              </button>
              <button
                type="button"
                onClick={() => setChartMode('candle')}
                className={`px-2 py-1 text-[10px] font-semibold uppercase tracking-wider rounded-md transition-all ${
                  chartMode === 'candle'
                    ? 'bg-white text-slate-800 shadow-sm'
                    : 'text-slate-500 hover:text-slate-700 hover:bg-white/40'
                }`}
              >
                Candle
              </button>
            </div>
          </div>
        </div>
      </div>

      <div className="relative flex-1 w-full min-h-[320px] px-2 py-4">
        <div
          ref={chartContainerRef}
          className={`absolute inset-0 right-12 bottom-6 overscroll-contain ${isDragging ? 'cursor-grabbing' : 'cursor-grab'}`}
          onMouseMove={handleMouseMove}
          onMouseLeave={handleMouseLeave}
          onMouseDown={beginDrag}
          onWheel={handleWheel}
        >
          <svg className="w-full h-full" viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`} preserveAspectRatio="none">
            <defs>
              <linearGradient id="chartGradient" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#3B82F6" stopOpacity="0.25" />
                <stop offset="100%" stopColor="#3B82F6" stopOpacity="0" />
              </linearGradient>
            </defs>

            <g stroke="rgba(148,163,184,0.45)" strokeDasharray="3 3" strokeWidth="0.6">
              {priceTicks.map((tick, i) => (
                <line key={`grid-p-${i}`} x1="0" y1={tick.y} x2={CHART_WIDTH} y2={tick.y} />
              ))}
              {timeGridXs.map((x, index) => (
                <line key={`grid-x-${index}`} x1={x} y1="0" x2={x} y2={CHART_HEIGHT} />
              ))}
            </g>

            <line x1="0" y1={VOLUME_TOP} x2={CHART_WIDTH} y2={VOLUME_TOP} stroke="rgba(148,163,184,0.7)" strokeWidth="0.6" />
            <g stroke="rgba(255,255,255,0.15)" strokeDasharray="2 2" strokeWidth="0.5">
              {volumeTicks.map((tick, i) => (
                <line key={`grid-v-${i}`} x1="0" y1={tick.y} x2={CHART_WIDTH} y2={tick.y} />
              ))}
            </g>

            {chartMetrics && visibleSeries.length > 0 && useLineChart && linePaths && (
              <>
                <path d={linePaths.areaPath} fill="url(#chartGradient)" />
                <path d={linePaths.linePath} fill="none" stroke="#3B82F6" strokeWidth="1.6" />
                {visibleSeries.map((point, index) => {
                  const step = CHART_WIDTH / Math.max(visibleSeries.length, 1);
                  const centerX = step * index + step / 2;
                  const candleWidth = Math.max(2, Math.min(12, step * 0.6));
                  const isUp = point.close >= point.open;
                  const color = isUp ? '#10B981' : '#F43F5E';
                  const volumeHeight = volumeMax > 0 ? (point.volume / volumeMax) * VOLUME_HEIGHT : 0;
                  const volumeY = VOLUME_TOP + (VOLUME_HEIGHT - volumeHeight);
                  return (
                    <rect
                      key={`vol-${point.bucketStart ?? index}`}
                      x={centerX - candleWidth / 2}
                      y={volumeY}
                      width={candleWidth}
                      height={volumeHeight}
                      fill={color}
                      opacity="0.6"
                    />
                  );
                })}
              </>
            )}

            {chartMetrics && visibleSeries.length > 0 && !useLineChart && (
              <>
                {visibleSeries.map((point, index) => {
                  const step = CHART_WIDTH / Math.max(visibleSeries.length, 1);
                  const centerX = step * index + step / 2;
                  const candleWidth = Math.max(2, Math.min(12, step * 0.6));
                  const { min, range } = chartMetrics;
                  const yHigh = PRICE_HEIGHT - ((point.high - min) / range) * PRICE_HEIGHT;
                  const yLow = PRICE_HEIGHT - ((point.low - min) / range) * PRICE_HEIGHT;
                  const yOpen = PRICE_HEIGHT - ((point.open - min) / range) * PRICE_HEIGHT;
                  const yClose = PRICE_HEIGHT - ((point.close - min) / range) * PRICE_HEIGHT;
                  const isUp = point.close >= point.open;
                  const color = isUp ? '#10B981' : '#F43F5E';
                  const bodyTop = Math.min(yOpen, yClose);
                  const bodyHeight = Math.max(2, Math.abs(yClose - yOpen));
                  const volumeHeight = volumeMax > 0 ? (point.volume / volumeMax) * VOLUME_HEIGHT : 0;
                  const volumeY = VOLUME_TOP + (VOLUME_HEIGHT - volumeHeight);
                  return (
                    <g key={point.bucketStart ?? index}>
                      <line x1={centerX} y1={yHigh} x2={centerX} y2={yLow} stroke={color} strokeWidth="1" />
                      <rect
                        x={centerX - candleWidth / 2}
                        y={bodyTop}
                        width={candleWidth}
                        height={bodyHeight}
                        fill={color}
                        rx="1"
                      />
                      <rect
                        x={centerX - candleWidth / 2}
                        y={volumeY}
                        width={candleWidth}
                        height={volumeHeight}
                        fill={color}
                        opacity="0.6"
                      />
                    </g>
                  );
                })}
              </>
            )}
          </svg>

          {crosshair && (
            <>
              <div
                className="absolute inset-y-0 border-l border-blue-500/50"
                style={{ left: `${crosshair.xRatio * 100}%` }}
              />
              <div
                className="absolute inset-x-0 border-t border-blue-500/50"
                style={{ top: `${crosshair.yRatio * 100}%` }}
              />
            </>
          )}

          {crosshair && hoveredPoint && (
            <div
              className="absolute pointer-events-none z-10 w-56 rounded-lg border border-white/60 bg-white/80 text-[10px] text-slate-700 shadow-lg backdrop-blur-sm"
              style={{
                left: `${crosshair.xRatio * 100}%`,
                top: `${crosshair.yRatio * 100}%`,
                transform:
                  crosshair.xRatio > 0.7
                    ? `translate(calc(-100% - 12px), ${crosshair.yRatio > 0.7 ? '-100%' : '-50%'})`
                    : `translate(12px, ${crosshair.yRatio > 0.7 ? '-100%' : '-50%'})`,
              }}
            >
              <div className="border-b border-white/60 px-2 py-1 font-semibold text-slate-600">Kline Detail</div>
              <div className="grid grid-cols-[92px_1fr] gap-x-2 gap-y-1 px-2 py-2">
                <span className="text-slate-500">bucketStart</span>
                <span className="text-slate-800">{formatDateTime(hoveredPoint.point.bucketStart)}</span>
                <span className="text-slate-500">bucketEnd</span>
                <span className="text-slate-800">{formatDateTime(hoveredPoint.point.bucketEnd)}</span>
                <span className="text-slate-500">openPrice</span>
                <span className="text-slate-800">{formatAxisNumber(hoveredPoint.point.open)}</span>
                <span className="text-slate-500">highPrice</span>
                <span className="text-slate-800">{formatAxisNumber(hoveredPoint.point.high)}</span>
                <span className="text-slate-500">lowPrice</span>
                <span className="text-slate-800">{formatAxisNumber(hoveredPoint.point.low)}</span>
                <span className="text-slate-500">closePrice</span>
                <span className="text-slate-800">{formatAxisNumber(hoveredPoint.point.close)}</span>
                <span className="text-slate-500">volume</span>
                <span className="text-slate-800">{formatVolumeNumber(hoveredPoint.point.volume)}</span>
                <span className="text-slate-500">turnover</span>
                <span className="text-slate-800">{formatVolumeNumber(hoveredPoint.point.turnover)}</span>
                <span className="text-slate-500">tradeCount</span>
                <span className="text-slate-800">{formatOptionalNumber(hoveredPoint.point.tradeCount, formatCountNumber)}</span>
              </div>
            </div>
          )}

          {!loading && !series.length && (
            <div className="absolute inset-0 flex items-center justify-center text-xs text-slate-400">
              No data
            </div>
          )}
        </div>
        <div className="absolute right-0 top-0 bottom-6 w-12 pr-1">
          {priceTicks.map((tick, index) => (
            <div
              key={`price-${index}`}
              className="absolute right-0 text-[10px] text-slate-400 font-mono text-right"
              style={{ top: `${(tick.y / CHART_HEIGHT) * 100}%`, transform: 'translateY(-50%)' }}
            >
              {formatAxisNumber(tick.value)}
            </div>
          ))}
          {crosshair && (
            <div
              className="absolute -right-1 text-[10px] font-mono text-right bg-blue-500 text-white px-2 py-0.5 rounded-l-md shadow-sm"
              style={{ top: `${crosshair.yRatio * 100}%`, transform: 'translateY(-50%)' }}
            >
              {crosshair.mode === 'volume' ? formatVolumeNumber(crosshair.value) : formatAxisNumber(crosshair.value)}
            </div>
          )}
          {volumeTicks.map((tick, index) => (
            <div
              key={`vol-${index}`}
              className="absolute right-0 text-[9px] text-slate-400 font-mono text-right"
              style={{ top: `${(tick.y / CHART_HEIGHT) * 100}%`, transform: 'translateY(-50%)' }}
            >
              {formatVolumeNumber(tick.value)}
            </div>
          ))}
        </div>
        <div className="absolute left-0 right-12 bottom-0 h-6">
          {timeTicks.map((tick, index) => (
            <div
              key={`time-${index}`}
              className="absolute bottom-0 text-[10px] text-slate-400 font-mono text-center"
              style={{ left: `${(tick.x / CHART_WIDTH) * 100}%`, transform: 'translateX(-50%)' }}
            >
              {tick.label}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
