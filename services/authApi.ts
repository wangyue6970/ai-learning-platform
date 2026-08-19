import { fetch } from 'expo/fetch';
import { API_BASE_URL } from './apiConfig';
import { readApiErrorMessage } from './apiClient';

export type LoginResult = {
  accessToken: string;
  tokenType: 'Bearer';
  expiresInSeconds: number;
  username: string;
};

type Credentials = {
  username: string;
  password: string;
};

export async function registerAccount(credentials: Credentials): Promise<void> {
  const response = await request('/api/auth/register', credentials, '无法连接注册服务');

  if (!response.ok) {
    throw new Error(await readApiErrorMessage(response, '注册失败，请稍后重试'));
  }
}

export async function loginAccount(credentials: Credentials): Promise<LoginResult> {
  const response = await request('/api/auth/login', credentials, '无法连接登录服务');

  if (!response.ok) {
    throw new Error(await readApiErrorMessage(response, '登录失败，请稍后重试'));
  }

  return response.json();
}

async function request(path: string, body: Credentials, networkErrorMessage: string): Promise<Response> {
  try {
    return await fetch(`${API_BASE_URL}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
  } catch {
    throw new Error(networkErrorMessage);
  }
}
