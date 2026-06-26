import { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';

import '../styles/CCTVPage.css';
import logo from '../assets/png/APAP로고.png';

function CCTVPage() {
  const videoRef = useRef(null);
  const navigate = useNavigate();

  useEffect(() => {
    let stream;
    const videoElement = videoRef.current;

    const startCamera = async () => {
      try {
        stream = await navigator.mediaDevices.getUserMedia({
          video: true,
          audio: false,
        });

        if (videoElement) {
          videoElement.srcObject = stream;
        }
      } catch (error) {
        console.error('카메라 접근 실패:', error);
      }
    };

    startCamera();

    return () => {
      if (stream) {
        stream.getTracks().forEach((track) => track.stop());

        if (videoElement) {
          videoElement.srcObject = null;
        }
      }
    };
  }, []);

  const handleBack = () => {
    if (videoRef.current?.srcObject) {
      videoRef.current.srcObject.getTracks().forEach((track) => track.stop());

      videoRef.current.srcObject = null;
    }

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
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted
          className="camera-video"
        />
      </div>
    </div>
  );
}

export default CCTVPage;
