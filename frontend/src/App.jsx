import { BrowserRouter, Navigate, Routes, Route } from 'react-router-dom';

import LoginPage from './pages/LoginPage';
import MainPage from './pages/MainPage';
import CCTVPage from './pages/CCTVPage';
import SavedVideoPage from './pages/SavedVideoPage';
import AlertPage from './pages/AlertPage';
import TermsPage from './pages/TermsPage';
import ProtectedRoute from './components/ProtectedRoute';
import { AuthProvider } from './context/AuthContext';
import { AlertStreamProvider } from './context/AlertStreamProvider';

import './App.css';

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        {/* 실시간 감지 알림 토스트는 화면과 무관하게 떠야 해서 라우트 바깥을 감싼다 */}
        <AlertStreamProvider>
          <Routes>
            <Route path="/" element={<LoginPage />} />
            <Route path="/terms" element={<TermsPage />} />

            <Route element={<ProtectedRoute />}>
              <Route path="/main" element={<MainPage />} />

              <Route path="/cctv" element={<CCTVPage />} />

              <Route path="/saved-video" element={<SavedVideoPage />} />

              <Route path="/alert" element={<AlertPage />} />
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </AlertStreamProvider>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
