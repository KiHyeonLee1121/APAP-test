import '../styles/AlertToast.css';

const formatTime = (sentAt) => {
  if (!sentAt) {
    return '';
  }
  const date = new Date(sentAt);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  return date.toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' });
};

/**
 * 화면 우상단에 뜨는 비정상 감지 알림.
 * 표시 내용은 백엔드 AlertResponse를 그대로 쓴다.
 */
function AlertToast({ toasts, onDismiss, onOpen }) {
  if (toasts.length === 0) {
    return null;
  }

  return (
    <div className="alert-toast-stack" role="region" aria-label="실시간 감지 알림">
      {toasts.map((toast) => (
        <div key={toast.id} className="alert-toast" role="alert">
          <div className="alert-toast__bar" aria-hidden="true" />

          <button
            type="button"
            className="alert-toast__body"
            onClick={() => onOpen?.(toast)}
          >
            <p className="alert-toast__title">비정상 행동 감지</p>

            <p className="alert-toast__message">{toast.message}</p>

            <p className="alert-toast__meta">
              {toast.videoSourceName ? `${toast.videoSourceName} · ` : ''}
              {formatTime(toast.sentAt)}
            </p>
          </button>

          <button
            type="button"
            className="alert-toast__close"
            onClick={() => onDismiss(toast.id)}
            aria-label="알림 닫기"
          >
            ×
          </button>
        </div>
      ))}
    </div>
  );
}

export default AlertToast;
