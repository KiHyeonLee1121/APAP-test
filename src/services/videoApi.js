import { apiRequest } from './authApi';

export const fetchVideos = async () => {
  const payload = await apiRequest('/api/videos');

  return payload?.data ?? [];
};

export const uploadVideoFile = async (file) => {
  const formData = new FormData();

  formData.append('file', file);

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
