import apiClient from './client';

export type RiskLimitResponse = {
    instrumentId: number | string;
    initialMarginRate?: number | string | null;
    maxLeverage?: number | string | null;
    maintenanceMarginRate?: number | string | null;
    liquidationFeeRate?: number | string | null;
    isActive?: boolean | null;
    updatedAt?: string | null;
};

export const getRiskLimit = async (instrumentId: string | number) => {
    const response = await apiClient.get(`/risk/api/risk/limits/${instrumentId}`);
    return response.data;
};
