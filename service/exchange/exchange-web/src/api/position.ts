import apiClient from './client';

export type PositionResponse = {
  positionId: number;
  userId: number;
  instrumentId: number;
  side: string;
  leverage: number;
  margin: string;
  entryPrice: string;
  quantity: string;
  closingReservedQuantity: string;
  markPrice: string;
  marginRatio: string;
  unrealizedPnl: string;
  cumRealizedPnl: string;
  cumFee: string;
  cumFundingFee: string;
  liquidationPrice: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  closedAt: string | null;
};

export type PositionEventItem = {
  eventId: number;
  positionId: number;
  userId: number;
  instrumentId: number;
  eventType: string;
  sequenceNumber: number;
  payload: string;
  referenceType: string;
  referenceId: string;
  occurredAt: string;
  createdAt: string;
};

export type PositionEventResponse = {
  positionId: number;
  snapshotAt: string;
  events: PositionEventItem[];
};

export const getPositions = async (instrumentId?: string | number, userId?: string | number) => {
  const response = await apiClient.get('/position/api/positions', {
    params: {
      instrumentId,
      userId,
    },
  });
  return response.data;
};

export const getPositionEvents = async (positionId: number | string) => {
  const response = await apiClient.get(`/position/api/positions/${positionId}/events`);
  return response.data;
};
