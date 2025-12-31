import apiClient from './client';

export type OrderResponse = {
  orderId: number;
  userId: number;
  instrumentId: number;
  clientOrderId: string | null;
  side: string;
  type: string;
  price: string | null;
  quantity: string;
  intent: string | null;
  filledQuantity: string;
  remainingQuantity: string;
  avgFillPrice: string | null;
  fee: string | null;
  status: string;
  rejectedReason: string | null;
  version: number | null;
  createdAt: string;
  updatedAt: string | null;
  submittedAt: string | null;
  filledAt: string | null;
  cancelledAt: string | null;
};

export type OrderSide = 'BUY' | 'SELL';
export type OrderType = 'LIMIT' | 'MARKET';

export type OrderCreateRequest = {
  instrumentId: number;
  side: OrderSide;
  type: OrderType;
  quantity: number | string;
  price?: number | string | null;
  clientOrderId?: string;
};

export const createOrder = async (data: OrderCreateRequest) => {
  const response = await apiClient.post<any>('/order/api/orders', data);
  return response.data;
};

export const getOrders = async (instrumentId?: string | number, userId?: string | number) => {
  const response = await apiClient.get('/order/api/orders', {
    params: {
      instrumentId,
      userId,
    },
  });
  return response.data;
};
