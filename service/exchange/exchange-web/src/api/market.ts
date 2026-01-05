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
  markPriceChangeRate?: string | null;
  calculatedAt: string | null;
};

export type KlineResponse = {
  instrumentId: number;
  period: string;
  bucketStart: string;
  bucketEnd: string;
  open: string;
  high: string;
  low: string;
  close: string;
  volume: string | null;
  turnover: string | null;
  tradeCount?: number | string | null;
  takerBuyVolume?: string | null;
  takerBuyTurnover?: string | null;
  isClosed?: boolean | null;
};

export type OrderBookLevel = {
  price: string | number | null;
  quantity: string | number | null;
};

export type OrderBookResponse = {
  instrumentId: number;
  bids: OrderBookLevel[] | null;
  asks: OrderBookLevel[] | null;
  bestBid: string | number | null;
  bestAsk: string | number | null;
  midPrice: string | number | null;
  updatedAt: string | null;
};

export const getTicker = async (instrumentId: string | number) => {
  const response = await apiClient.get(`/market/api/market/tickers/${instrumentId}`);
  return response.data;
};

export const getOrderBook = async (instrumentId: string | number) => {
  const response = await apiClient.get(`/market/api/market/orderbook/${instrumentId}`);
  return response.data;
};

export const getMarkPrice = async (instrumentId: string | number) => {
  const response = await apiClient.get(`/market/api/market/mark-price/${instrumentId}`);
  return response.data;
};

export const getKlines = async (instrumentId: string | number, period: string, limit = 200) => {
  const response = await apiClient.get('/market/api/market/kline', {
    params: {
      instrumentId,
      period,
      limit,
    },
  });
  return response.data;
};
