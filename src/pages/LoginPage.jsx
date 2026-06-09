import '../styles/LoginPage.css';

import { useNavigate } from 'react-router-dom';

import logo from '../assets/png/APAP로고.png';
import googleLogo from '../assets/png/Google logo.png';

function LoginPage() {
  const navigate = useNavigate();

  const handleGoogleLogin = () => {
    /*
      MVP 단계
      ↓
      메인페이지 이동

      추후 Google OAuth 연결 시

      window.location.href =
      'http://localhost:8080/oauth2/authorization/google';
    */

    navigate('/main');
  };

  return (
    <div className="login-container">
      <div className="login-wrapper">
        <div className="logo-section">
          <img src={logo} alt="APAP" className="logo" />
        </div>

        <div className="google-section">
          <button className="google-login-btn" onClick={handleGoogleLogin}>
            <img src={googleLogo} alt="Google" className="google-logo" />

            <span className="google-text">Google 계정으로 로그인</span>
          </button>
        </div>
      </div>
    </div>
  );
}

export default LoginPage;
