import { useRef } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import logo from '../assets/png/APAP로고.png';

export default function MainPage() {
  const menuRef = useRef(null);
  const navigate = useNavigate();
  const { logout, user } = useAuth();
  const closeMenu = () => { menuRef.current.open = false; };
  const handleLogout = async () => {
    closeMenu();
    await logout();
    navigate('/', { replace: true });
  };
  return (
    <div className="app-layout">
      <header className="app-header">
        <details className="app-menu" ref={menuRef} onKeyDown={(event) => {
          if (event.key === 'Escape') {
            closeMenu();
            menuRef.current.querySelector('summary').focus();
          }
        }}>
          <summary>☰ 메뉴</summary>
          <nav className="app-menu-panel" aria-label="주 메뉴">
            <p className="welcome-text">{user?.name || user?.email || '사용자'}님 안녕하세요</p>
            <NavLink to="/cctv" onClick={closeMenu}>실시간 영상</NavLink>
            <NavLink to="/saved-video" onClick={closeMenu}>영상분석</NavLink>
            <NavLink to="/alert" onClick={closeMenu}>알림 내역</NavLink>
            <NavLink to="/terms" onClick={closeMenu}>이용약관</NavLink>
            <button onClick={handleLogout}>로그아웃</button>
          </nav>
        </details>
        <img src={logo} alt="APAP" className="app-logo" />
      </header>
      <main className="app-content"><Outlet /></main>
    </div>
  );
}
