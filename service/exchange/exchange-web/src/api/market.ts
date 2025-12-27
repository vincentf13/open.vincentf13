import apiClient from './client';

export type TickerResponse = {
  instrumentId: number;
  lastPrice: string | null;
  volume24h: string | null;
  turnover24h: string | null;
  high24h: string | null;
  low24h: string | null;
  open24h: string | null;
  priceChange24h: string | null;
  priceChangePct: string | null;
  capturedAt: string | null;
};

export type MarkPriceResponse = {
  instrumentId: number;
  markPrice: string | null;
  calculatedAt: string | null;
};

export const getTicker = async (instrumentId: string | number) => {
  const response = await apiClient.get(`/market/api/market/tickers/${instrumentId}`);
  return response.data;
};

export const getMarkPrice = async (instrumentId: string | number) => {
  const response = await apiClient.get(`/market/api/market/mark-price/${instrumentId}`);
  return response.data;
};
