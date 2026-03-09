import apiClient from './client';

export const resetSystemData = async () => {
    const response = await apiClient.post('/admin/api/admin/system/reset-data');
    return response.data;
};
