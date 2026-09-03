import { getApiBaseUrl } from '../config/env';
import { getStoredAccessToken } from './authStorage';

let defaultAccessToken = getStoredAccessToken();

const getFrontendOrigin = () => window.location.origin;

const resolveAccessToken = (token) => {
  if (token !== undefined) {
    return token || '';
  }

  return getStoredAccessToken() || defaultAccessToken || '';
};

const buildApiUrl = (path) => {
  const apiBaseUrl = getApiBaseUrl();

  if (!apiBaseUrl) {
    throw new Error(
      'API Base URL이 설정되지 않았습니다. .env.local에 VITE_API_BASE_URL을 설정해주세요.',
    );
  }

  return `${apiBaseUrl}${path}`;
};

const parseResponseBody = async (response) => {
  const text = await response.text();

  if (!text) {
    return null;
  }

  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
};

const getConnectionErrorMessage = () => {
  const backendOrigin = getApiBaseUrl() || 'VITE_API_BASE_URL 미설정';

  return [
    '백엔드 서버에 연결할 수 없습니다.',
    `프론트 주소: ${getFrontendOrigin()}`,
    `백엔드 주소: ${backendOrigin}`,
    '서버가 꺼져 있거나 CORS 문제가 있다면 백엔드 CORS 설정에서 프론트 주소를 허용해야 합니다.',
  ].join(' ');
};

const getPayloadErrorMessage = (payload) => {
  if (typeof payload === 'string') {
    return payload;
  }

  if (typeof payload === 'object' && payload !== null) {
    return payload.error?.message || payload.message || '';
  }

  return '';
};

export const apiRequest = async (
  path,
  { method = 'GET', body, headers = {}, token, auth = true } = {},
) => {
  const requestHeaders = { ...headers };
  const accessToken = resolveAccessToken(token);
  const isFormData = body instanceof FormData;

  if (body !== undefined && !isFormData) {
    requestHeaders['Content-Type'] = 'application/json';
  }

  if (auth && accessToken) {
    requestHeaders.Authorization = `Bearer ${accessToken}`;
  }

  let response;

  try {
    response = await fetch(buildApiUrl(path), {
      method,
      headers: requestHeaders,
      body:
        body === undefined
          ? undefined
          : isFormData
            ? body
            : JSON.stringify(body),
    });
  } catch {
    throw new Error(getConnectionErrorMessage());
  }

  const payload = await parseResponseBody(response);

  if (!response.ok || payload?.success === false) {
    throw new Error(
      getPayloadErrorMessage(payload) || `요청에 실패했습니다. (${response.status})`,
    );
  }

  return payload;
};

export const setDefaultAuthorizationHeader = (accessToken) => {
  defaultAccessToken = accessToken || '';
};

export const clearDefaultAuthorizationHeader = () => {
  defaultAccessToken = '';
};

export const loginWithGoogleToken = async (idToken) => {
  const payload = await apiRequest('/api/auth/google', {
    method: 'POST',
    body: { idToken },
    auth: false,
  });

  return payload?.data;
};

export const fetchCurrentUser = async (token) => {
  const payload = await apiRequest('/api/auth/me', { token });

  return payload?.data;
};

export const deleteCurrentUser = async (token) =>
  apiRequest('/api/auth/me', {
    method: 'DELETE',
    token,
  });

export const logoutWithAccessToken = async (token) => {
  await apiRequest('/api/auth/logout', {
    method: 'POST',
    token,
  });
};
