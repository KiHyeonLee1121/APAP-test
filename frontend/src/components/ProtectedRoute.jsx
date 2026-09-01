import { Navigate, Outlet } from 'react-router-dom';

import { useAuth } from '../hooks/useAuth';

function ProtectedRoute() {
  const { isAuthenticated, isInitializing } = useAuth();

  if (isInitializing) {
    return (
      <div className="route-loading">
        <p>로그인 정보를 확인하는 중...</p>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}

export default ProtectedRoute;
