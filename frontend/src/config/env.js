export const getApiBaseUrl = () =>
  (import.meta.env.VITE_API_BASE_URL || '').trim().replace(/\/+$/, '');

export const getGoogleClientId = () =>
  (import.meta.env.VITE_GOOGLE_CLIENT_ID || '').trim();

export const isGoogleClientIdMissing = (clientId) =>
  !clientId ||
  clientId.includes('여기에') ||
  !clientId.endsWith('.apps.googleusercontent.com');
