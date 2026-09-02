import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import '../styles/SavedVideoPage.css';
import logo from '../assets/png/APAP로고.png';

function SavedVideoPage() {
  const navigate = useNavigate();

  const [normalVideos, setNormalVideos] = useState([]);
  const [abnormalVideos, setAbnormalVideos] = useState([]);

  const normalInputRef = useRef(null);
  const abnormalInputRef = useRef(null);

  const addNormalVideo = (e) => {
    const files = Array.from(e.target.files);

    const urls = files.map((file) => ({
      name: file.name,
      url: URL.createObjectURL(file),
    }));

    setNormalVideos((prev) => [...prev, ...urls]);
  };

  const addAbnormalVideo = (e) => {
    const files = Array.from(e.target.files);

    const urls = files.map((file) => ({
      name: file.name,
      url: URL.createObjectURL(file),
    }));

    setAbnormalVideos((prev) => [...prev, ...urls]);
  };

  return (
    <div className="saved-video-container">
      <div className="top-bar">
        <button className="back-button" onClick={() => navigate('/main')}>
          ←
        </button>

        <img src={logo} alt="APAP" className="top-logo" />
      </div>

      <div className="white-wrapper">
        {/* 정상 행동 */}
        <div className="video-section">
          <h2>정상적인 행동 동영상</h2>

          <div className="video-grid">
            {normalVideos.map((video, index) => (
              <div key={index} className="video-card">
                <video controls>
                  <source src={video.url} />
                </video>

                <p>{video.name}</p>
              </div>
            ))}
          </div>

          <button
            className="add-btn"
            onClick={() => normalInputRef.current.click()}
          >
            추가하기
          </button>

          <input
            type="file"
            accept="video/*"
            multiple
            hidden
            ref={normalInputRef}
            onChange={addNormalVideo}
          />
        </div>

        {/* 비정상 행동 */}
        <div className="video-section">
          <h2>비정상적인 행동 동영상</h2>

          <div className="video-grid">
            {abnormalVideos.map((video, index) => (
              <div key={index} className="video-card">
                <video controls>
                  <source src={video.url} />
                </video>

                <p>{video.name}</p>
              </div>
            ))}
          </div>

          <button
            className="add-btn"
            onClick={() => abnormalInputRef.current.click()}
          >
            추가하기
          </button>

          <input
            type="file"
            accept="video/*"
            multiple
            hidden
            ref={abnormalInputRef}
            onChange={addAbnormalVideo}
          />
        </div>
      </div>
    </div>
  );
}

export default SavedVideoPage;
