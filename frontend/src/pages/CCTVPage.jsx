import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import '../styles/CCTVPage.css';
import logo from '../assets/png/APAP로고.png';
import { getAiServerUrl } from '../config/env';

function CCTVPage() {
  const imgRef = useRef(null);
  const navigate = useNavigate();
  const [status, setStatus] = useState('connecting');
  // 브라우저 캐시로 이전 스트림이 재사용되지 않도록 진입할 때마다 새 URL을 만든다.
  const [streamKey] = useState(() => Date.now());

  const streamUrl = `${getAiServerUrl()}/stream/live?t=${streamKey}`;

  const stopStream = () => {
    // <img>는 src를 비워야 서버와의 MJPEG 연결이 끊긴다.
    if (imgRef.current) {
      imgRef.current.src = '';
    }
  };

  useEffect(() => stopStream, []);

  const handleBack = () => {
    stopStream();
    navigate('/main');
  };

  return (
    <div className="cctv-container">
      <div className="top-bar">
        <button className="back-button" onClick={handleBack}>
          ←
        </button>

        <img src={logo} alt="APAP" className="top-logo" />
      </div>

      <div className="camera-wrapper">
        <img
          ref={imgRef}
          src={streamUrl}
          alt="실시간 카메라"
          className="camera-video"
          onLoad={() => setStatus('live')}
          onError={() => setStatus('error')}
        />

        {status === 'connecting' && (
          <p className="camera-message">카메라에 연결하는 중입니다...</p>
        )}

        {status === 'error' && (
          <p className="camera-message camera-message--error">
            카메라 스트림에 연결할 수 없습니다.
            <br />
            AI 서버가 실행 중인지, RTSP_URL이 설정되어 있는지 확인해 주세요.
          </p>
        )}
      </div>
    </div>
  );
}

export default CCTVPage;
