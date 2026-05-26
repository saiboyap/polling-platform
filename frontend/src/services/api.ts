import axios, { AxiosError } from 'axios';
import { useAuthStore } from '../store/authStore';

const RETRY_COUNT = 3;
const RETRY_DELAY_MS = 2000;

// Status codes that should NOT be retried (client errors, auth failures)
const NO_RETRY_STATUSES = new Set([400, 401, 403, 404, 409, 422]);

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  headers: { 'Content-Type': 'application/json' },
  timeout: 30000,
});

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  config.headers['X-Correlation-ID'] = Math.random().toString(36).slice(2, 10);
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    if (error.response?.status === 401) {
      useAuthStore.getState().clearAuth();
      window.location.href = '/login';
      return Promise.reject(error);
    }

    const config = error.config as typeof error.config & { _retryCount?: number };
    if (!config) return Promise.reject(error);

    // Don't retry definitive client errors or if retries are exhausted
    const status = error.response?.status;
    if (status && NO_RETRY_STATUSES.has(status)) return Promise.reject(error);

    config._retryCount = config._retryCount ?? 0;
    if (config._retryCount >= RETRY_COUNT) return Promise.reject(error);

    config._retryCount += 1;
    await new Promise((resolve) => setTimeout(resolve, RETRY_DELAY_MS));
    return api(config);
  }
);

export default api;
