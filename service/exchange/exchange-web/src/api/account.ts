import apiClient from './client';

export const getAccountBalances = async (asset: string) => {
  const response = await apiClient.get('/account/api/account/balances', {
    params: { asset },
  });
  return response.data;
};
