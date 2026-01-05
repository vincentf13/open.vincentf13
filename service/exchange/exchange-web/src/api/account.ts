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

export type AccountBalanceItem = {
  accountId: number;
  accountCode: string;
  accountName: string;
  category: string;
  instrumentId: number;
  asset: string;
  balance: string;
  available: string;
  reserved: string;
  version: number;
  updatedAt: string;
};

export type AccountBalanceSheetResponse = {
  userId: number;
  snapshotAt: string;
  assets: AccountBalanceItem[];
  liabilities: AccountBalanceItem[];
  equity: AccountBalanceItem[];
  expenses: AccountBalanceItem[];
  revenue: AccountBalanceItem[];
};

export const getBalanceSheet = async () => {
  const response = await apiClient.get('/account/api/account/balance-sheet');
  return response.data;
};
