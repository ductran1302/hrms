import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import keycloak from './keycloak';

interface AuthContextValue {
  initialized: boolean;
  initFailed: boolean;
  authenticated: boolean;
  username: string;
  roles: string[];
  hasRole: (...roles: string[]) => boolean;
  login: () => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

interface InitState {
  initialized: boolean;
  initFailed: boolean;
  authenticated: boolean;
  username: string;
  roles: string[];
}

const initial: InitState = {
  initialized: false,
  initFailed: false,
  authenticated: false,
  username: '',
  roles: [],
};

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<InitState>(initial);

  useEffect(() => {
    keycloak
      .init({ onLoad: 'login-required', pkceMethod: 'S256' })
      .then(() => {
        setState({
          initialized: true,
          initFailed: false,
          authenticated: keycloak.authenticated === true,
          username: keycloak.tokenParsed?.preferred_username ?? '',
          roles: (keycloak.tokenParsed?.realm_access as { roles?: string[] } | undefined)?.roles ?? [],
        });
        // Không refresh theo interval (P1): 2 luồng refresh đồng thời (interval +
        // axios interceptor) có thể đua nhau gây refresh token lỗi. Chỉ refresh
        // lazy ngay trước mỗi request qua axios interceptor (api/client.ts).
      })
      .catch(() => setState((s) => ({ ...s, initialized: true, initFailed: true })));
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      ...state,
      hasRole: (...need) => need.some((role) => state.roles.includes(role)),
      login: () => keycloak.login(),
      logout: () => keycloak.logout({ redirectUri: window.location.origin }),
    }),
    [state],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth phải được dùng trong <AuthProvider>');
  return ctx;
}
