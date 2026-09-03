import { BrowserRouter, Navigate, Routes, Route } from 'react-router-dom';

import LoginPage from './pages/LoginPage';
import MainPage from './pages/MainPage';
import CCTVPage from './pages/CCTVPage';
import SavedVideoPage from './pages/SavedVideoPage';
import AlertPage from './pages/AlertPage';
import TermsPage from './pages/TermsPage';
import ProtectedRoute from './components/ProtectedRoute';
import { AuthProvider } from './context/AuthContext';

import './App.css';

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
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
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
