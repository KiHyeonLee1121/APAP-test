import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import '../styles/CCTVPage.css';
import logo from '../assets/png/APAP로고.png';
import { getAiServerUrl } from '../config/env';
import { fetchVideos } from '../services/videoApi';

function CCTVPage() {
  const imgRef = useRef(null);
  const navigate = useNavigate();
  const [status, setStatus] = useState('connecting');
  // 브라우저 캐시로 이전 스트림이 재사용되지 않도록 진입할 때마다 새 URL을 만든다.
  const [streamKey] = useState(() => Date.now());
  // 등록된 CCTV 영상 소스를 찾기 전에는 스트림을 열지 않는다 — 찾은 뒤 한 번만
  // 연결해야 video_source_id 없이 붙었다가 다시 연결하는 이중 연결을 피할 수 있다.
  const [cameraLookup, setCameraLookup] = useState({ done: false, videoSourceId: null });

  useEffect(() => {
    let isCancelled = false;

    fetchVideos()
      .then((videos) => {
        if (isCancelled) return;
        const camera = videos.find((video) => video.type === 'CCTV');
        setCameraLookup({ done: true, videoSourceId: camera ? camera.id : null });
      })
      .catch(() => {
        if (!isCancelled) {
          setCameraLookup({ done: true, videoSourceId: null });
        }
      });

    return () => {
      isCancelled = true;
    };
  }, []);

  const streamUrl = cameraLookup.videoSourceId
    ? `${getAiServerUrl()}/stream/live?t=${streamKey}&video_source_id=${cameraLookup.videoSourceId}`
    : `${getAiServerUrl()}/stream/live?t=${streamKey}`;

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
        {cameraLookup.done && (
          <img
            ref={imgRef}
            src={streamUrl}
            alt="실시간 카메라"
            className="camera-video"
            onLoad={() => setStatus('live')}
            onError={() => setStatus('error')}
          />
        )}

        {(!cameraLookup.done || status === 'connecting') && (
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
