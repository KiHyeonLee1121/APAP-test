import {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react';

import { AuthContext } from './authContextValue';
import {
  clearDefaultAuthorizationHeader,
  fetchCurrentUser,
  loginWithGoogleToken,
  logoutWithAccessToken,
  setDefaultAuthorizationHeader,
} from '../services/authApi';
import {
  clearAuthStorage,
  getStoredAccessToken,
  getStoredUser,
  setAuthStorage,
} from '../services/authStorage';

export function AuthProvider({ children }) {
  const [accessToken, setAccessToken] = useState(() => getStoredAccessToken());
  const [user, setUser] = useState(() => getStoredUser());
  const [isInitializing, setIsInitializing] = useState(() =>
    Boolean(getStoredAccessToken()),
  );
  const [authError, setAuthError] = useState('');

  const persistAuth = useCallback((nextAccessToken, nextUser) => {
    setAuthStorage(nextAccessToken, nextUser);
    setDefaultAuthorizationHeader(nextAccessToken);
    setAccessToken(nextAccessToken);
    setUser(nextUser);
  }, []);

  const resetAuth = useCallback((nextAuthError = '') => {
    clearAuthStorage();
    clearDefaultAuthorizationHeader();
    setAccessToken('');
    setUser(null);
    setIsInitializing(false);
    setAuthError(nextAuthError);
  }, []);

  const logout = useCallback(async () => {
    const logoutToken = getStoredAccessToken();

    try {
      if (logoutToken) {
        await logoutWithAccessToken(logoutToken);
      }
    } catch {
      // 서버 로그아웃 실패나 만료 토큰(401)도 클라이언트 로그아웃은 계속 진행합니다.
    } finally {
      resetAuth();
    }
  }, [resetAuth]);

  const loginWithGoogle = useCallback(
    async (idToken) => {
      setAuthError('');

      const authData = await loginWithGoogleToken(idToken);

      if (!authData?.accessToken || !authData?.user) {
        throw new Error('로그인 응답 형식이 올바르지 않습니다.');
      }

      persistAuth(authData.accessToken, authData.user);

      return authData.user;
    },
    [persistAuth],
  );

  useEffect(() => {
    if (!accessToken) {
      return undefined;
    }

    let isCancelled = false;

    const loadCurrentUser = async () => {
      setIsInitializing(true);

      try {
        const currentUser = await fetchCurrentUser(accessToken);

        if (isCancelled) {
          return;
        }

        if (!currentUser) {
          throw new Error('현재 로그인 사용자 정보를 확인할 수 없습니다.');
        }

        setUser(currentUser);
        setAuthStorage(accessToken, currentUser);
        setAuthError('');
      } catch (error) {
        if (isCancelled) {
          return;
        }

        resetAuth(error.message || '로그인 정보를 확인하지 못했습니다.');
      } finally {
        if (!isCancelled) {
          setIsInitializing(false);
        }
      }
    };

    loadCurrentUser();

    return () => {
      isCancelled = true;
    };
  }, [accessToken, resetAuth]);

  const value = useMemo(
    () => ({
      accessToken,
      user,
      isAuthenticated: Boolean(accessToken),
      isInitializing,
      authError,
      loginWithGoogle,
      logout,
    }),
    [
      accessToken,
      user,
      isInitializing,
      authError,
      loginWithGoogle,
      logout,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
