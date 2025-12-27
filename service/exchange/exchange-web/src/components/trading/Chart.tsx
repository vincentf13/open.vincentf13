
import { useEffect, useMemo, useState } from 'react';

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
  bucketStart?: string;
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

export default function Chart({ instrumentId }: ChartProps) {
  const [period, setPeriod] = useState<(typeof periods)[number]>('1h');
  const [klines, setKlines] = useState<KlineResponse[]>([]);
  const [loading, setLoading] = useState(false);

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
    return sorted
      .map((item) => ({
        open: Number(item.open),
        close: Number(item.close),
        high: Number(item.high),
        low: Number(item.low),
        volume: Number(item.volume ?? 0),
        bucketStart: item.bucketStart,
      }))
      .filter((item) => Number.isFinite(item.close));
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

  const cursor = useMemo(() => {
    if (!series.length || !chartMetrics) {
      return null;
    }
    const { min, range } = chartMetrics;
    const lastClose = series[series.length - 1].close;
    const y = PRICE_HEIGHT - ((lastClose - min) / range) * PRICE_HEIGHT;
    return { price: lastClose, y };
  }, [series, chartMetrics]);

  const axisLabels = useMemo(() => {
    if (!chartMetrics) {
      return ['-', '-', '-', '-'];
    }
    const { min, max } = chartMetrics;
    const mid = (min + max) / 2;
    const upperMid = (mid + max) / 2;
    const lowerMid = (mid + min) / 2;
    return [max, upperMid, lowerMid, min].map(formatAxisNumber);
  }, [chartMetrics]);

  return (
    <div className="flex flex-col h-full bg-white/5">
      <div className="flex items-center justify-end border-b border-white/20 px-4 py-3">
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
      </div>

      <div className="relative flex-1 w-full min-h-[300px] px-2 py-4">
        <div className="absolute inset-0 top-12 bottom-8 left-0 right-0">
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
                  const color = isUp ? '#3B82F6' : '#93C5FD';
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
                <line x1="0" y1={VOLUME_TOP} x2={CHART_WIDTH} y2={VOLUME_TOP} stroke="rgba(255,255,255,0.35)" strokeWidth="0.5" />
              </>
            )}
          </svg>

          {cursor && (
            <div
              className="absolute right-0 w-full border-t border-blue-500/50 border-dashed flex items-center justify-end"
              style={{ top: `${cursor.y}px` }}
            >
              <span className="bg-blue-500 text-white text-[10px] px-2 py-0.5 rounded-l-md font-mono shadow-sm">
                {formatAxisNumber(cursor.price)}
              </span>
            </div>
          )}

          {!loading && !series.length && (
            <div className="absolute inset-0 flex items-center justify-center text-xs text-slate-400">
              No data
            </div>
          )}
        </div>

        <div className="absolute right-0 top-12 bottom-8 w-12 pr-2 flex flex-col justify-between text-[10px] text-slate-400 font-mono text-right select-none">
          {axisLabels.map((label) => (
            <span key={label}>{label}</span>
          ))}
        </div>
      </div>
    </div>
  );
}
