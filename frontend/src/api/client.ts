import axios, { AxiosError } from 'axios';
import keycloak from '../auth/keycloak';
import { API_BASE } from '../config';
import i18n from '../i18n';
import type { ApiError } from './types';

const client = axios.create({ baseURL: API_BASE });

client.interceptors.request.use(async (config) => {
  try {
    await keycloak.updateToken(30);
  } catch {
    // token hiện tại vẫn còn hạn dùng; nếu hết hạn, response interceptor xử lý 401
  }
  if (keycloak.token) config.headers.Authorization = `Bearer ${keycloak.token}`;
  return config;
});

client.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    if (error.response?.status === 401) keycloak.login();
    return Promise.reject(error);
  },
);

export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as ApiError | undefined;
    if (body?.message) {
      const fields = body.fieldErrors?.map((f: { field: string; message: string }) => `${f.field}: ${f.message}`).join('; ');
      return fields ? `${body.message} — ${fields}` : body.message;
    }
    return error.message;
  }
  return i18n.t('unknownError');
}

export default client;
