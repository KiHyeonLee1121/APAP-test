export const getApiBaseUrl = () =>
  (import.meta.env.VITE_API_BASE_URL || '').trim().replace(/\/+$/, '');

// AI 서버는 실시간 카메라 스트림(MJPEG)을 직접 내보내므로 백엔드와 별도 주소가 필요하다.
export const getAiServerUrl = () =>
  (import.meta.env.VITE_AI_SERVER_URL || 'http://localhost:8000')
    .trim()
    .replace(/\/+$/, '');

export const getGoogleClientId = () =>
  (import.meta.env.VITE_GOOGLE_CLIENT_ID || '').trim();

export const isGoogleClientIdMissing = (clientId) =>
  !clientId ||
  clientId.includes('여기에') ||
  !clientId.endsWith('.apps.googleusercontent.com');
