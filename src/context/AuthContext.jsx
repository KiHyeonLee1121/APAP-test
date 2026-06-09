import {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react';

import { AuthContext } from './authContextValue';
import { fetchCurrentUser, loginWithGoogleToken } from '../services/authApi';
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
    setAccessToken(nextAccessToken);
    setUser(nextUser);
  }, []);

  const logout = useCallback(() => {
    clearAuthStorage();
    setAccessToken('');
    setUser(null);
    setIsInitializing(false);
    setAuthError('');
  }, []);

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

        clearAuthStorage();
        setAccessToken('');
        setUser(null);
        setAuthError(error.message || '로그인 정보를 확인하지 못했습니다.');
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
  }, [accessToken]);

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
