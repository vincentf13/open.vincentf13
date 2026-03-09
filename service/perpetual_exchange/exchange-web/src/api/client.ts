import axios from 'axios';

export const AUTH_REQUIRED_EVENT = 'auth:required';
export const REFRESH_AFTER_LOGIN_KEY = 'refresh_after_login';

export const hasToken = () => {
    const token = localStorage.getItem('accessToken');
    return !!token && token !== 'undefined' && token !== 'null';
};

const apiClient = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:12345',
    timeout: 10000,
    headers: {
        'Content-Type': 'application/json',
    },
});

// 請求攔截器：自動添加 Token
apiClient.interceptors.request.use((config) => {
    const token = localStorage.getItem('accessToken');
    const requestUrl = config.url || '';
    const isLoginRequest = requestUrl.includes('/auth/api/login');
    if (!isLoginRequest && token && token !== 'undefined' && token !== 'null') {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

// 響應攔截器：統一錯誤處理
apiClient.interceptors.response.use(
    (response) => response,
    (error) => {
        const status = error.response?.status;
        const requestUrl = error.config?.url || '';
        const errorCode = error.response?.data?.code;
        const isLoginRequest = requestUrl.includes('/auth/api/login');

        if ((status === 401 || status === 403) && !isLoginRequest && errorCode !== 'AUTH_BAD_CREDENTIALS') {
            localStorage.removeItem('accessToken');
            window.dispatchEvent(new Event(AUTH_REQUIRED_EVENT));
        }
        return Promise.reject(error);
    }
);

export default apiClient;
