import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import '../styles/AlertPage.css';
import logo from '../assets/png/APAP로고.png';
import { fetchAlerts, markAlertAsRead } from '../services/alertApi';

const ALERT_STATUS_LABELS = {
  PENDING: '대기',
  SENT: '미확인',
  FAILED: '실패',
  READ: '읽음',
};

const formatAlertTime = (sentAt) => {
  if (!sentAt) return '';
  const date = new Date(sentAt);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleString('ko-KR', {
    month: 'long',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
};

function AlertPage() {
  const navigate = useNavigate();

  const [alerts, setAlerts] = useState([]);
  const [selectedAlerts, setSelectedAlerts] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isUpdating, setIsUpdating] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  useEffect(() => {
    let isCancelled = false;

    const loadAlerts = (isInitialLoad) =>
      fetchAlerts()
        .then((loadedAlerts) => {
          if (!isCancelled) {
            setAlerts(loadedAlerts);
            if (isInitialLoad) {
              setErrorMessage('');
            }
          }
        })
        .catch((error) => {
          // 실시간 감지 중 폴링 한 번 실패했다고 화면을 에러로 덮지 않는다 —
          // 초기 로딩 실패일 때만 에러 상태를 보여준다.
          if (!isCancelled && isInitialLoad) {
            setErrorMessage(error.message || '알림 내역을 불러오지 못했습니다.');
          }
        })
        .finally(() => {
          if (!isCancelled && isInitialLoad) {
            setIsLoading(false);
          }
        });

    loadAlerts(true);
    // 실시간 감지로 새 알림이 오면 새로고침 없이 보이도록 주기적으로 다시 불러온다.
    const intervalId = setInterval(() => loadAlerts(false), 5000);

    return () => {
      isCancelled = true;
      clearInterval(intervalId);
    };
  }, []);

  const toggleSelect = (alertId) => {
    if (selectedAlerts.includes(alertId)) {
      setSelectedAlerts(selectedAlerts.filter((item) => item !== alertId));
    } else {
      setSelectedAlerts([...selectedAlerts, alertId]);
    }
  };

  const markSelectedAsRead = async () => {
    if (selectedAlerts.length === 0) {
      return;
    }

    setIsUpdating(true);
    setErrorMessage('');

    try {
      const readAlerts = await Promise.all(
        selectedAlerts.map((alertId) => markAlertAsRead(alertId)),
      );
      const readAlertMap = new Map(readAlerts.map((alert) => [alert.id, alert]));

      setAlerts((currentAlerts) =>
        currentAlerts.map((alert) => readAlertMap.get(alert.id) || alert),
      );
      setSelectedAlerts([]);
    } catch (error) {
      setErrorMessage(error.message || '선택한 알림을 읽음 처리하지 못했습니다.');
    } finally {
      setIsUpdating(false);
    }
  };

  const markAllAsRead = async () => {
    const unreadAlertIds = alerts
      .filter((alert) => alert.status !== 'READ')
      .map((alert) => alert.id);

    if (unreadAlertIds.length === 0) {
      return;
    }

    setIsUpdating(true);
    setErrorMessage('');

    try {
      const readAlerts = await Promise.all(
        unreadAlertIds.map((alertId) => markAlertAsRead(alertId)),
      );
      const readAlertMap = new Map(readAlerts.map((alert) => [alert.id, alert]));

      setAlerts((currentAlerts) =>
        currentAlerts.map((alert) => readAlertMap.get(alert.id) || alert),
      );
    } catch (error) {
      setErrorMessage(error.message || '전체 알림을 읽음 처리하지 못했습니다.');
    } finally {
      setIsUpdating(false);
    }

    setSelectedAlerts([]);
  };

  return (
    <div className="alert-container">
      <div className="top-bar">
        <button className="back-button" onClick={() => navigate('/main')}>
          ←
        </button>

        <img src={logo} alt="APAP" className="top-logo" />
      </div>

      <div className="alert-wrapper">
        <div className="alert-header">
          <h1>알림 내역</h1>

          <div className="alert-buttons">
            <button
              onClick={markAllAsRead}
              disabled={isLoading || isUpdating || alerts.length === 0}
            >
              전체 읽음
            </button>

            <button
              onClick={markSelectedAsRead}
              disabled={isLoading || isUpdating || selectedAlerts.length === 0}
            >
              선택 읽음
            </button>
          </div>
        </div>

        <div className="alert-list">
          {isLoading ? (
            <div className="alert-empty-state">알림을 불러오는 중입니다.</div>
          ) : errorMessage ? (
            <div className="alert-empty-state is-error">{errorMessage}</div>
          ) : alerts.length === 0 ? (
            <div className="alert-empty-state">알림 내역이 없습니다.</div>
          ) : (
            alerts.map((alert) => (
              <div
                key={alert.id}
                className={`alert-item ${
                  selectedAlerts.includes(alert.id) ? 'selected' : ''
                } ${alert.status === 'READ' ? 'is-read' : ''}`}
                onClick={() => toggleSelect(alert.id)}
              >
                <div className="alert-content">
                  <span className="alert-message">{alert.message}</span>
                  <span className="alert-time">{formatAlertTime(alert.sentAt)}</span>
                </div>
                <span className="alert-status">
                  {ALERT_STATUS_LABELS[alert.status] || alert.status}
                </span>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}

export default AlertPage;
