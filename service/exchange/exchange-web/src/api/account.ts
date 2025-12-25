import apiClient from './client';

export const getAccountBalances = async (asset: string) => {
  const response = await apiClient.get('/account/api/account/balances', {
    params: { asset },
  });
  return response.data;
};

export const depositAccount = async (payload: {
  userId: number;
  asset: string;
  amount: string;
  txId: string;
  creditedAt: string;
}) => {
  const response = await apiClient.post('/account/api/account/deposits', payload);
  return response.data;
};

export const withdrawAccount = async (payload: {
  userId: number;
  asset: string;
  amount: string;
  txId: string;
  creditedAt: string;
}) => {
  const response = await apiClient.post('/account/api/account/withdrawals', payload);
  return response.data;
};
