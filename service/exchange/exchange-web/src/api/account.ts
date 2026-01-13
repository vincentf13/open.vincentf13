import apiClient from './client';

export const getAccountBalances = async (asset: string) => {
    const response = await apiClient.get('/account/api/account/balances', {
        params: {asset},
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
    available: string | null;
    reserved: string | null;
    version: number;
    createdAt: string;
    updatedAt: string;
};

export type AccountBalanceSheetResponse = {
    userId: number;
    snapshotAt: string;
    earliestSnapshotAt: string;
    latestSnapshotAt: string;
    assets: AccountBalanceItem[];
    liabilities: AccountBalanceItem[];
    equity: AccountBalanceItem[];
    expenses: AccountBalanceItem[];
    revenue: AccountBalanceItem[];
};

export const getBalanceSheet = async (snapshotAt?: string) => {
    const response = await apiClient.get('/account/api/account/balance-sheet', {
        params: snapshotAt ? {snapshotAt} : undefined,
    });
    return response.data;
};

export type AccountJournalItem = {
    journalId: number;
    userId: number;
    accountId: number;
    accountCode: string | null;
    accountName: string | null;
    category: string;
    asset: string;
    amount: string;
    direction: string;
    balanceAfter: string;
    referenceType: string;
    referenceId: string;
    seq: number;
    description: string | null;
    eventTime: string;
    createdAt: string | null;
};

export type AccountJournalResponse = {
    userId: number;
    accountId: number;
    snapshotAt: string;
    journals: AccountJournalItem[];
};

export const getAccountJournals = async (accountId: number, snapshotAt?: string) => {
    const response = await apiClient.get('/account/api/account/journals', {
        params: snapshotAt ? {accountId, snapshotAt} : {accountId},
    });
    return response.data;
};

export type PlatformJournalItem = {
    journalId: number;
    accountId: number;
    accountCode?: string;
    accountName?: string;
    category: string;
    asset: string;
    amount: string;
    direction: string;
    balanceAfter: string;
    referenceType: string;
    referenceId: string;
    seq: number;
    description: string | null;
    eventTime: string;
    createdAt: string | null;
};

export type AccountReferenceJournalResponse = {
    userId: number;
    referenceType: string;
    referenceIdPrefix: string;
    snapshotAt: string;
    accountJournals: AccountJournalItem[];
    platformJournals: PlatformJournalItem[];
};

export const getJournalsByReference = async (referenceType: string, referenceId: string) => {
    const response = await apiClient.get('/account/api/account/journals/by-reference', {
        params: {referenceType, referenceId},
    });
    return response.data;
};

export type PlatformAccountItem = {
    accountId: number;
    accountCode: string;
    accountName: string;
    category: string;
    asset: string;
    balance: string;
    version: number;
    createdAt: string | null;
    updatedAt: string | null;
};

export type PlatformAccountResponse = {
    snapshotAt: string;
    assets: PlatformAccountItem[];
    liabilities: PlatformAccountItem[];
    equity: PlatformAccountItem[];
    expenses: PlatformAccountItem[];
    revenue: PlatformAccountItem[];
};

export const getPlatformAccounts = async (snapshotAt?: string) => {
    const response = await apiClient.get('/account/api/account/platform-accounts', {
        params: snapshotAt ? {snapshotAt} : undefined,
    });
    return response.data;
};

export type PlatformAccountJournalResponse = {
    accountId: number;
    snapshotAt: string;
    journals: PlatformJournalItem[];
};

export const getPlatformAccountJournals = async (accountId: number, snapshotAt?: string) => {
    const response = await apiClient.get('/account/api/account/platform-accounts/journals', {
        params: snapshotAt ? {accountId, snapshotAt} : {accountId},
    });
    return response.data;
};
