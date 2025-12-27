
import { useEffect, useMemo, useState, type MouseEvent } from 'react';

import { getKlines, type KlineResponse } from '../../api/market';

type ChartProps = {
  instrumentId: string | null;
};

type SeriesPoint = {
  open: number;
  close: number;
  high: number;
  low: number;
  volume: number;
  turnover: number;
  tradeCount: number;
  takerBuyVolume: number;
  takerBuyTurnover: number;
  isClosed: boolean;
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

const buildRandomSeries = (items: KlineResponse[]) => {
  let lastClose = 67200;
  return items.map((item) => {
    const baseSeed = toNumber(item.close) ?? toNumber(item.open) ?? lastClose;
    const base = baseSeed > 0 ? baseSeed : lastClose;
    const swing = base * (0.002 + Math.random() * 0.006);
    const open = base + (Math.random() - 0.5) * swing;
    const close = base + (Math.random() - 0.5) * swing;
    const high = Math.max(open, close) + Math.random() * swing * 0.6;
    const low = Math.min(open, close) - Math.random() * swing * 0.6;
    const volumeBase = 120;
    const volume = Math.max(1, volumeBase * (0.4 + Math.random() * 1.2));
    const turnover = volume * ((open + close) / 2);
    const tradeCount = Math.max(1, Math.floor(20 + Math.random() * 180));
    const takerBuyVolume = volume * (0.3 + Math.random() * 0.5);
    const takerBuyTurnover = takerBuyVolume * ((open + close) / 2);
    const isClosed = Math.random() > 0.2;
    lastClose = close;
    return {
      open,
      close,
      high,
      low,
      volume,
      turnover,
      tradeCount,
      takerBuyVolume,
      takerBuyTurnover,
      isClosed,
      bucketStart: item.bucketStart,
      bucketEnd: item.bucketEnd,
    };
  });
};

export default function Chart({ instrumentId }: ChartProps) {
  const [period, setPeriod] = useState<(typeof periods)[number]>('1h');
  const [klines, setKlines] = useState<KlineResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [crosshair, setCrosshair] = useState<{
    xRatio: number;
    yRatio: number;
    mode: 'price' | 'volume';
    value: number;
  } | null>(null);

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
  }, [instrumentId, period]);

  const series = useMemo<SeriesPoint[]>(() => {
    if (!klines.length) {
      return [];
    }
    const sorted = [...klines].sort((a, b) => {
      const aTime = a.bucketStart ? new Date(a.bucketStart).getTime() : 0;
      const bTime = b.bucketStart ? new Date(b.bucketStart).getTime() : 0;
      return aTime - bTime;
    });
    return buildRandomSeries(sorted).filter((item) => Number.isFinite(item.close));
  }, [klines]);

  const chartMetrics = useMemo(() => {
    if (!series.length) {
      return null;
    }
    const highs = series.map((point) => point.high);
    const lows = series.map((point) => point.low);
    const max = Math.max(...highs);
    const min = Math.min(...lows);
    const range = max - min || 1;
    return { min, max, range };
  }, [series]);

  const volumeMax = useMemo(() => {
    if (!series.length) {
      return 0;
    }
    return Math.max(...series.map((point) => point.volume || 0));
  }, [series]);

  const handleMouseMove = (event: MouseEvent<HTMLDivElement>) => {
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
    setCrosshair(null);
  };

  const priceTicks = useMemo(() => {
    if (!chartMetrics) {
      return [];
    }
    const { min, max, range } = chartMetrics;
    const mid = (min + max) / 2;
    const upperMid = (mid + max) / 2;
    const lowerMid = (mid + min) / 2;
    const values = [max, upperMid, lowerMid, min];
    return values.map((value) => {
      const y = PRICE_HEIGHT - ((value - min) / range) * PRICE_HEIGHT;
      return {
        value,
        y: clamp(y, 6, PRICE_HEIGHT - 6),
      };
    });
  }, [chartMetrics]);

  const volumeTicks = useMemo(() => {
    if (!volumeMax || !Number.isFinite(volumeMax)) {
      return [];
    }
    const values = [volumeMax, volumeMax / 2, 0];
    return values.map((value) => {
      const ratio = volumeMax > 0 ? value / volumeMax : 0;
      const y = VOLUME_TOP + (1 - ratio) * VOLUME_HEIGHT;
      return {
        value,
        y: clamp(y, VOLUME_TOP + 8, VOLUME_TOP + VOLUME_HEIGHT - 2),
      };
    });
  }, [volumeMax]);

  const timeTicks = useMemo(() => {
    if (!series.length) {
      return [];
    }
    const size = series.length;
    const step = CHART_WIDTH / Math.max(size, 1);
    const indices = [0, Math.floor((size - 1) / 3), Math.floor(((size - 1) * 2) / 3), size - 1];
    const unique = Array.from(new Set(indices)).filter((idx) => idx >= 0 && idx < size);
    return unique.map((idx) => {
      const label = formatTimeLabel(series[idx]?.bucketStart, period);
      return {
        x: step * idx + step / 2,
        label,
      };
    });
  }, [series, period]);

  const hoveredPoint = useMemo(() => {
    if (!crosshair || !series.length) {
      return null;
    }
    const index = Math.min(series.length - 1, Math.max(0, Math.floor(crosshair.xRatio * series.length)));
    return {
      index,
      point: series[index],
    };
  }, [crosshair, series]);

  return (
    <div className="flex flex-col h-full bg-white/5">
      <div className="flex items-center justify-end border-b border-white/20 px-4 py-3">
        <div className="flex flex-col items-end gap-1">
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
          <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider">
            KLINE COUNTS {loading ? '...' : series.length}
          </span>
        </div>
      </div>

      <div className="relative flex-1 w-full min-h-[320px] px-2 py-4">
        <div
          className="absolute inset-0 right-12 bottom-6"
          onMouseMove={handleMouseMove}
          onMouseLeave={handleMouseLeave}
        >
          <svg className="w-full h-full" viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`} preserveAspectRatio="none">
            <defs>
              <linearGradient id="chartGradient" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#3B82F6" stopOpacity="0.25" />
                <stop offset="100%" stopColor="#3B82F6" stopOpacity="0" />
              </linearGradient>
            </defs>

            <g stroke="rgba(255,255,255,0.4)" strokeDasharray="4 4" strokeWidth="0.5">
              <line x1="0" y1={PRICE_HEIGHT * 0.25} x2={CHART_WIDTH} y2={PRICE_HEIGHT * 0.25} />
              <line x1="0" y1={PRICE_HEIGHT * 0.5} x2={CHART_WIDTH} y2={PRICE_HEIGHT * 0.5} />
              <line x1="0" y1={PRICE_HEIGHT * 0.75} x2={CHART_WIDTH} y2={PRICE_HEIGHT * 0.75} />
              <line x1={CHART_WIDTH * 0.25} y1="0" x2={CHART_WIDTH * 0.25} y2={PRICE_HEIGHT} />
              <line x1={CHART_WIDTH * 0.5} y1="0" x2={CHART_WIDTH * 0.5} y2={PRICE_HEIGHT} />
              <line x1={CHART_WIDTH * 0.75} y1="0" x2={CHART_WIDTH * 0.75} y2={PRICE_HEIGHT} />
            </g>

            <line x1="0" y1={VOLUME_TOP} x2={CHART_WIDTH} y2={VOLUME_TOP} stroke="rgba(148,163,184,0.7)" strokeWidth="0.6" />

            {chartMetrics && series.length > 0 && (
              <>
                {series.map((point, index) => {
                  const step = CHART_WIDTH / Math.max(series.length, 1);
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
                  const bodyHeight = Math.max(1, Math.abs(yClose - yOpen));
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
                <span className="text-slate-800">{hoveredPoint.point.tradeCount}</span>
                <span className="text-slate-500">takerBuyVolume</span>
                <span className="text-slate-800">{formatVolumeNumber(hoveredPoint.point.takerBuyVolume)}</span>
                <span className="text-slate-500">takerBuyTurnover</span>
                <span className="text-slate-800">{formatVolumeNumber(hoveredPoint.point.takerBuyTurnover)}</span>
                <span className="text-slate-500">isClosed</span>
                <span className="text-slate-800">{String(hoveredPoint.point.isClosed)}</span>
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
              style={{ top: `${(tick.y / CHART_HEIGHT) * 100}%` }}
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
              style={{ top: `${(tick.y / CHART_HEIGHT) * 100}%` }}
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
