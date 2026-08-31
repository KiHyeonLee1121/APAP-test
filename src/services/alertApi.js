import { apiRequest } from './authApi';

export const fetchAlerts = async () => {
  const payload = await apiRequest('/api/alerts');

  return payload?.data ?? [];
};

export const markAlertAsRead = async (alertId) => {
  const payload = await apiRequest(`/api/alerts/${alertId}/read`, {
    method: 'PATCH',
  });

  return payload?.data;
};
