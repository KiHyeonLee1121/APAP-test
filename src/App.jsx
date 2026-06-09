import { BrowserRouter, Routes, Route } from 'react-router-dom';

import LoginPage from './pages/LoginPage';
import MainPage from './pages/MainPage';
import CCTVPage from './pages/CCTVPage';
import SavedVideoPage from './pages/SavedVideoPage';
import AlertPage from './pages/AlertPage';

import './App.css';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<LoginPage />} />

        <Route path="/main" element={<MainPage />} />

        <Route path="/cctv" element={<CCTVPage />} />

        <Route path="/saved-video" element={<SavedVideoPage />} />

        <Route path="/alert" element={<AlertPage />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
