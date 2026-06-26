import '../styles/LoginPage.css';

import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { getGoogleClientId, isGoogleClientIdMissing } from '../config/env';
import { useGoogleIdentityButton } from '../hooks/useGoogleIdentityButton';
import { useAuth } from '../hooks/useAuth';

import logo from '../assets/png/APAP로고.png';
import googleLogo from '../assets/png/Google logo.png';

function LoginPage() {
  const navigate = useNavigate();
  const { authError, isAuthenticated, isInitializing, loginWithGoogle } =
    useAuth();
  const googleClientId = getGoogleClientId();
  const hasGoogleClientIdError = isGoogleClientIdMissing(googleClientId);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState(() =>
    hasGoogleClientIdError
      ? 'Google Client ID가 설정되지 않았습니다. .env.local에 VITE_GOOGLE_CLIENT_ID를 설정해주세요.'
      : '',
  );

  useEffect(() => {
    if (!isInitializing && isAuthenticated) {
      navigate('/main', { replace: true });
    }
  }, [isAuthenticated, isInitializing, navigate]);

  const handleGoogleCredential = useCallback(
    async (credential) => {
      setIsSubmitting(true);
      setErrorMessage('');

      try {
        await loginWithGoogle(credential);
        navigate('/main', { replace: true });
      } catch (error) {
        setErrorMessage(error.message || 'Google 로그인에 실패했습니다.');
      } finally {
        setIsSubmitting(false);
      }
    },
    [loginWithGoogle, navigate],
  );

  const handleGoogleError = useCallback((error) => {
    setErrorMessage(error.message || 'Google 로그인에 실패했습니다.');
  }, []);

  const googleButtonRef = useGoogleIdentityButton({
    clientId: googleClientId,
    disabled: hasGoogleClientIdError,
    onCredential: handleGoogleCredential,
    onError: handleGoogleError,
  });
  const displayedErrorMessage = errorMessage || authError;

  return (
    <div className="login-container">
      <div className="login-wrapper">
        <div className="logo-section">
          <img src={logo} alt="APAP" className="logo" />
        </div>

        <div className="google-section">
          {hasGoogleClientIdError ? (
            <button className="google-login-btn disabled" disabled>
              <img src={googleLogo} alt="Google" className="google-logo" />

              <span className="google-text">Google 로그인 설정 필요</span>
            </button>
          ) : (
            <div
              ref={googleButtonRef}
              className={`google-button-host ${
                isSubmitting ? 'is-submitting' : ''
              }`}
            />
          )}

          {isSubmitting && <p className="login-status">로그인 중...</p>}

          {displayedErrorMessage && (
            <p className="login-error" role="alert">
              {displayedErrorMessage}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}

export default LoginPage;
