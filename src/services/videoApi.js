import { getApiBaseUrl } from '../config/env';
import { getStoredAccessToken } from './authStorage';

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

export const uploadVideoFile = async (file) => {
  const formData = new FormData();
  const accessToken = getStoredAccessToken();
  const headers = {};

  formData.append('file', file);

  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }

  let response;

  try {
    response = await fetch(buildApiUrl('/api/videos/upload'), {
      method: 'POST',
      headers,
      body: formData,
    });
  } catch {
    throw new Error('백엔드 서버에 연결할 수 없습니다.');
  }

  const payload = await parseResponseBody(response);

  if (!response.ok || payload?.success === false) {
    throw new Error(
      payload?.message ||
        payload?.error?.message ||
        `동영상 업로드에 실패했습니다. (${response.status})`,
    );
  }

  return payload?.data;
};
