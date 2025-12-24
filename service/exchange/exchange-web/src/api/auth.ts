import apiClient from './client';

// 登入
export const login = async (email: string, password: string) => {
  const response = await apiClient.post('/auth/api/login', {
    email,
    password,
  });
  return response.data;
};

// 註冊
export const register = async (email: string, password: string) => {
  const response = await apiClient.post('/user/api/user', {
    email,
    password,
    externalId: `user-${Date.now()}`,
  });
  return response.data;
};
