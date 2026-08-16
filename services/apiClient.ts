import { fetch } from 'expo/fetch';
import { API_BASE_URL } from './apiConfig';

type ApiRequestOptions = {
  method?: string;
  headers?: Record<string, string>;
  body?: BodyInit;
};

let accessToken: string | null = null;

export function setApiAccessToken(nextAccessToken: string | null) {
  accessToken = nextAccessToken;
}

export function apiFetch(path: string, options: ApiRequestOptions = {}) {
  return fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      ...options.headers,
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    },
  });
}
