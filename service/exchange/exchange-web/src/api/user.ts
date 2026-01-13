import apiClient from './client';

// 獲取當前使用者資訊
export const getCurrentUser = async () => {
    const response = await apiClient.get('/user/api/user/me');
    return response.data;
};
