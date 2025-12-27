import apiClient from './client';

export type TradeResponse = {
  tradeId: number;
  instrumentId: number;
  makerUserId: number;
  takerUserId: number;
  orderId: number;
  counterpartyOrderId: number;
  orderSide: string;
  counterpartyOrderSide: string;
  makerIntent: string;
  takerIntent: string;
  tradeType: string;
  price: string;
  quantity: string;
  totalValue: string;
  makerFee: string;
  takerFee: string;
  executedAt: string;
  createdAt: string;
};

export const getTradesByOrderId = async (orderId: number) => {
  const response = await apiClient.get('/matching/api/trades', {
    params: {
      orderId,
    },
  });
  return response.data;
};

export const getTradesByInstrument = async (instrumentId: string | number) => {
  const response = await apiClient.get('/matching/api/trades/by-instrument', {
    params: {
      instrumentId,
    },
  });
  return response.data;
};
