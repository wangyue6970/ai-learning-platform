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

/** 只把后端明确提供给用户的 message 交给页面显示。 */
export async function readApiErrorMessage(response: Response, fallbackMessage: string): Promise<string> {
  try {
    const errorBody: { message?: unknown; action?: unknown } = await response.json();
    const message = typeof errorBody.message === 'string' && errorBody.message.trim()
      ? errorBody.message
      : fallbackMessage;
    const action = typeof errorBody.action === 'string' && errorBody.action.trim()
      ? errorBody.action
      : null;
    return action ? `${message}\n\n${action}` : message;
  } catch {
    return fallbackMessage;
  }
}
