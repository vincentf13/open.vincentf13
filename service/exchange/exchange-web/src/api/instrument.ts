import apiClient from './client';

export type InstrumentSummary = {
  instrumentId: string;
  name: string;
  symbol?: string;
  instrumentType?: string;
  quoteAsset?: string;
  baseAsset?: string;
  contractSize?: number | string | null;
  takerFeeRate?: number | string | null;
  defaultLeverage?: number | string | null;
  [key: string]: unknown;
};

const CACHE_KEY = 'instrumentSummaries';
const SELECTED_KEY = 'selectedInstrumentId';

let instrumentCache: InstrumentSummary[] | null = null;

const normalizeInstrumentList = (list: any[]): InstrumentSummary[] => {
  return list
    .filter(Boolean)
    .map((item) => ({
      ...item,
      instrumentId: item?.instrumentId != null ? String(item.instrumentId) : '',
      name: item?.name ?? item?.symbol ?? '',
    }))
    .filter((item) => item.instrumentId);
};

export const getCachedInstrumentSummaries = (): InstrumentSummary[] => {
  if (instrumentCache) {
    return instrumentCache;
  }
  const cached = localStorage.getItem(CACHE_KEY);
  if (!cached) {
    return [];
  }
  try {
    const parsed = JSON.parse(cached);
    if (Array.isArray(parsed)) {
      instrumentCache = normalizeInstrumentList(parsed);
      return instrumentCache;
    }
  } catch (error) {
    return [];
  }
  return [];
};

export const getCachedInstrumentId = (): string | null => {
  const cached = localStorage.getItem(SELECTED_KEY);
  return cached || null;
};

export const setCachedInstrumentId = (instrumentId: string | null) => {
  if (!instrumentId) {
    localStorage.removeItem(SELECTED_KEY);
    return;
  }
  localStorage.setItem(SELECTED_KEY, instrumentId);
};

export const fetchInstrumentSummaries = async (): Promise<InstrumentSummary[]> => {
  const response = await apiClient.get('/admin/api/admin/instruments');
  const list = Array.isArray(response.data?.data) ? response.data.data : [];
  instrumentCache = normalizeInstrumentList(list);
  localStorage.setItem(CACHE_KEY, JSON.stringify(instrumentCache));
  return instrumentCache;
};
