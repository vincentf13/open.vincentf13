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

export const getOrders = async (instrumentId?: string | number, userId?: string | number) => {
  const response = await apiClient.get('/order/api/orders', {
    params: {
      instrumentId,
      userId,
    },
  });
  return response.data;
};
