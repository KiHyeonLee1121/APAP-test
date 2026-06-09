import { useState } from 'react';
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

  const [showWithdrawModal, setShowWithdrawModal] = useState(false);
  const userName = currentUser?.name || currentUser?.email || '사용자';

  const handleLogout = () => {
    logout();
    navigate('/', { replace: true });
  };

  const handleWithdraw = () => {
    alert('회원 탈퇴');

    // 추후 Spring Boot 연결
    // axios.delete('/users/me');

    logout();
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

          <p className="menu-title">저장된 영상</p>

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

        {/* 회원 탈퇴 */}
        <div className="menu-card" onClick={() => setShowWithdrawModal(true)}>
          <div className="image-box">
            <img src={user} alt="회원정보" />
          </div>

          <p className="menu-title">회원 탈퇴</p>

          <div className="menu-line"></div>
        </div>
      </div>

      {/* 탈퇴 모달 */}
      {showWithdrawModal && (
        <div className="modal-overlay">
          <div className="withdraw-modal">
            <div className="modal-message">정말 탈퇴하시겠습니까?</div>

            <div className="modal-buttons">
              <button className="modal-btn" onClick={handleWithdraw}>
                네
              </button>

              <button
                className="modal-btn"
                onClick={() => setShowWithdrawModal(false)}
              >
                아니오
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default MainPage;
