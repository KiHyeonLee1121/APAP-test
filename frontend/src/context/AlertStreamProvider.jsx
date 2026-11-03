import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import AlertToast from '../components/AlertToast';
import { useAuth } from '../hooks/useAuth';
import { subscribeAlertStream } from '../services/alertStream';

/** 동시에 띄울 토스트 최대 개수. 넘치면 오래된 것부터 밀어낸다. */
const MAX_VISIBLE = 3;

/** 자동으로 사라지기까지의 시간 */
const AUTO_DISMISS_MS = 8000;

/**
 * 로그인 상태면 알림 스트림에 붙어 있다가, 실시간 감지가 오면
 * 화면 우상단에 토스트를 띄운다. 어느 화면에 있든 동작한다.
 */
export function AlertStreamProvider({ children }) {
  const { accessToken, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  const [toasts, setToasts] = useState([]);
  // 재연결 직후 같은 알림이 다시 와도 중복으로 띄우지 않는다
  const seenIdsRef = useRef(new Set());
  const timersRef = useRef(new Map());

  const dismiss = useCallback((alertId) => {
    setToasts((current) => current.filter((toast) => toast.id !== alertId));

    const timer = timersRef.current.get(alertId);
    if (timer) {
      clearTimeout(timer);
      timersRef.current.delete(alertId);
    }
  }, []);

  const showToast = useCallback(
    (alert) => {
      if (!alert?.id || seenIdsRef.current.has(alert.id)) {
        return;
      }
      seenIdsRef.current.add(alert.id);

      setToasts((current) => [...current, alert].slice(-MAX_VISIBLE));

      timersRef.current.set(
        alert.id,
        setTimeout(() => dismiss(alert.id), AUTO_DISMISS_MS),
      );
    },
    [dismiss],
  );

  useEffect(() => {
    if (!isAuthenticated || !accessToken) {
      return undefined;
    }

    const unsubscribe = subscribeAlertStream({
      accessToken,
      onAlert: showToast,
    });
    const timers = timersRef.current;
    const seenIds = seenIdsRef.current;

    // 로그아웃·토큰 변경·화면 종료로 구독이 끊기면 떠 있던 토스트도 함께 치운다
    return () => {
      unsubscribe();
      timers.forEach((timer) => clearTimeout(timer));
      timers.clear();
      seenIds.clear();
      setToasts([]);
    };
  }, [accessToken, isAuthenticated, showToast]);

  const openAlertPage = useCallback(
    (alert) => {
      dismiss(alert.id);
      navigate('/alert');
    },
    [dismiss, navigate],
  );

  return (
    <>
      {children}
      <AlertToast toasts={toasts} onDismiss={dismiss} onOpen={openAlertPage} />
    </>
  );
}
