import { getApiBaseUrl } from '../config/env';
import { apiRequest } from './authApi';
import { getStoredAccessToken } from './authStorage';

export const fetchVideos = async () => {
  const payload = await apiRequest('/api/videos');

  return payload?.data ?? [];
};

export const uploadVideoFile = async (file, { name } = {}) => {
  const formData = new FormData();

  formData.append('file', file);

  if (name) {
    formData.append('name', name);
  }

  const payload = await apiRequest('/api/videos/upload', {
    method: 'POST',
    body: formData,
  });

  return payload?.data;
};

export const requestVideoAnalysis = async (videoSourceId) => {
  const payload = await apiRequest('/api/analysis/jobs', {
    method: 'POST',
    body: { videoSourceId },
  });

  return payload?.data;
};

export const resetVideos = async () => {
  const payload = await apiRequest('/api/videos/reset', {
    method: 'POST',
  });

  return payload;
};

const buildApiUrl = (path) => `${getApiBaseUrl()}${path}`;

const getBlobErrorMessage = async (response) => {
  const text = await response.text();

  if (!text) {
    return `영상을 불러오지 못했습니다. (${response.status})`;
  }

  try {
    const payload = JSON.parse(text);

    return payload.error?.message || payload.message || text;
  } catch {
    return text;
  }
};

export const fetchVideoContent = async (videoId) => {
  const accessToken = getStoredAccessToken();
  const headers = accessToken ? { Authorization: `Bearer ${accessToken}` } : {};

  let response;

  try {
    response = await fetch(buildApiUrl(`/api/videos/${videoId}/content`), {
      headers,
    });
  } catch {
    throw new Error('저장된 영상을 불러올 수 없습니다.');
  }

  if (!response.ok) {
    throw new Error(await getBlobErrorMessage(response));
  }

  return response.blob();
};
