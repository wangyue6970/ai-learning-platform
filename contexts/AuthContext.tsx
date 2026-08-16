import * as SecureStore from 'expo-secure-store';
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { loginAccount, registerAccount } from '../services/authApi';
import { setApiAccessToken } from '../services/apiClient';

const ACCESS_TOKEN_KEY = 'ai-learning.access-token';
const USERNAME_KEY = 'ai-learning.username';

type AuthContextValue = {
  accessToken: string | null;
  username: string | null;
  isRestoringSession: boolean;
  login: (username: string, password: string) => Promise<void>;
  registerAndLogin: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [username, setUsername] = useState<string | null>(null);
  const [isRestoringSession, setIsRestoringSession] = useState(true);

  const saveSession = useCallback(async (nextAccessToken: string, nextUsername: string) => {
    await Promise.all([
      SecureStore.setItemAsync(ACCESS_TOKEN_KEY, nextAccessToken),
      SecureStore.setItemAsync(USERNAME_KEY, nextUsername),
    ]);
    setApiAccessToken(nextAccessToken);
    setAccessToken(nextAccessToken);
    setUsername(nextUsername);
  }, []);

  useEffect(() => {
    let isMounted = true;

    async function restoreSession() {
      try {
        const [savedAccessToken, savedUsername] = await Promise.all([
          SecureStore.getItemAsync(ACCESS_TOKEN_KEY),
          SecureStore.getItemAsync(USERNAME_KEY),
        ]);

        if (isMounted && savedAccessToken) {
          setApiAccessToken(savedAccessToken);
          setAccessToken(savedAccessToken);
          setUsername(savedUsername);
        }
      } finally {
        if (isMounted) {
          setIsRestoringSession(false);
        }
      }
    }

    void restoreSession();

    return () => {
      isMounted = false;
    };
  }, []);

  const login = useCallback(async (inputUsername: string, password: string) => {
    const usernameToLogin = inputUsername.trim();
    const result = await loginAccount({ username: usernameToLogin, password });
    await saveSession(result.accessToken, result.username);
  }, [saveSession]);

  const registerAndLogin = useCallback(async (inputUsername: string, password: string) => {
    const usernameToRegister = inputUsername.trim();
    await registerAccount({ username: usernameToRegister, password });

    try {
      await login(usernameToRegister, password);
    } catch {
      throw new Error('注册成功，但自动登录失败，请点击“登录”重试');
    }
  }, [login]);

  const logout = useCallback(async () => {
    await SecureStore.deleteItemAsync(ACCESS_TOKEN_KEY);
    await SecureStore.deleteItemAsync(USERNAME_KEY).catch(() => undefined);
    setApiAccessToken(null);
    setAccessToken(null);
    setUsername(null);
  }, []);

  return (
    <AuthContext.Provider value={{ accessToken, username, isRestoringSession, login, registerAndLogin, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error('useAuth 必须在 AuthProvider 内使用');
  }

  return context;
}
