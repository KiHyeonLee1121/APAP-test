import { useNavigate } from 'react-router-dom';

import { useAuth } from '../hooks/useAuth';

import '../styles/MainPage.css';

import logo from '../assets/png/APAP로고.png';
import camera from '../assets/png/감시카메라.png';
import video from '../assets/png/저장된 영상.png';
import alarm from '../assets/png/알림 내역.png';
import user from '../assets/png/회원정보.png';

function MainPage() {
  const navigate = useNavigate();
  const { logout, user: currentUser } = useAuth();

  const userName = currentUser?.name || currentUser?.email || '사용자';

  const handleLogout = async () => {
    await logout();
    navigate('/', { replace: true });
  };

  return (
    <div className="main-container">
      <div className="left-section">
        <img src={logo} alt="APAP" className="main-logo" />

        <p className="welcome-text">{userName}님 안녕하세요</p>

        <button className="logout-btn" onClick={handleLogout}>
          로그아웃
        </button>
      </div>

      <div className="menu-grid">
        {/* 실시간 영상 */}
        <div className="menu-card" onClick={() => navigate('/cctv')}>
          <div className="image-box">
            <img src={camera} alt="실시간 영상" />
          </div>

          <p className="menu-title">실시간 영상</p>

          <div className="menu-line"></div>
        </div>

        {/* 저장된 영상 */}
        <div className="menu-card" onClick={() => navigate('/saved-video')}>
          <div className="image-box">
            <img src={video} alt="저장된 영상" />
          </div>

          <p className="menu-title">영상저장 및 분석</p>

          <div className="menu-line"></div>
        </div>

        {/* 알림 내역 */}
        <div className="menu-card" onClick={() => navigate('/alert')}>
          <div className="image-box">
            <img src={alarm} alt="알림 내역" />
          </div>

          <p className="menu-title">알림 내역</p>

          <div className="menu-line"></div>
        </div>

        {/* 이용약관 */}
        <div className="menu-card" onClick={() => navigate('/terms')}>
          <div className="image-box">
            <img src={user} alt="이용약관" />
          </div>

          <p className="menu-title">이용약관</p>

          <div className="menu-line"></div>
        </div>
      </div>
    </div>
  );
}

export default MainPage;
