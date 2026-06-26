import { useEffect, useRef } from 'react';

const GOOGLE_IDENTITY_SCRIPT_SRC = 'https://accounts.google.com/gsi/client';

let googleScriptPromise;

const loadGoogleIdentityScript = () => {
  if (window.google?.accounts?.id) {
    return Promise.resolve();
  }

  if (googleScriptPromise) {
    return googleScriptPromise;
  }

  googleScriptPromise = new Promise((resolve, reject) => {
    const existingScript = document.querySelector(
      `script[src="${GOOGLE_IDENTITY_SCRIPT_SRC}"]`,
    );

    if (existingScript) {
      existingScript.addEventListener('load', resolve, { once: true });
      existingScript.addEventListener('error', reject, { once: true });
      return;
    }

    const script = document.createElement('script');
    script.src = GOOGLE_IDENTITY_SCRIPT_SRC;
    script.async = true;
    script.defer = true;
    script.onload = resolve;
    script.onerror = () =>
      reject(new Error('Google 로그인 스크립트를 불러오지 못했습니다.'));

    document.head.appendChild(script);
  });

  return googleScriptPromise;
};

export const useGoogleIdentityButton = ({
  clientId,
  disabled,
  onCredential,
  onError,
}) => {
  const buttonRef = useRef(null);

  useEffect(() => {
    if (disabled || !clientId) {
      return undefined;
    }

    let isCancelled = false;
    const buttonElement = buttonRef.current;

    const renderGoogleButton = async () => {
      try {
        await loadGoogleIdentityScript();

        if (isCancelled || !buttonElement) {
          return;
        }

        const googleIdentity = window.google?.accounts?.id;

        if (!googleIdentity) {
          throw new Error('Google Identity Services를 사용할 수 없습니다.');
        }

        buttonElement.innerHTML = '';
        googleIdentity.initialize({
          client_id: clientId,
          callback: (response) => {
            if (response?.credential) {
              onCredential(response.credential);
              return;
            }

            onError(new Error('Google 인증 정보를 받지 못했습니다.'));
          },
        });

        const buttonWidth = Math.min(
          Math.max(Math.floor(buttonElement.offsetWidth || 320), 260),
          420,
        );

        googleIdentity.renderButton(buttonElement, {
          theme: 'outline',
          size: 'large',
          type: 'standard',
          shape: 'rectangular',
          text: 'signin_with',
          logo_alignment: 'left',
          locale: 'ko',
          width: buttonWidth,
        });
      } catch (error) {
        if (!isCancelled) {
          onError(error);
        }
      }
    };

    renderGoogleButton();

    return () => {
      isCancelled = true;

      if (buttonElement) {
        buttonElement.innerHTML = '';
      }
    };
  }, [clientId, disabled, onCredential, onError]);

  return buttonRef;
};
