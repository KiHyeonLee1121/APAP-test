import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import '../styles/SavedVideoPage.css';
import logo from '../assets/png/APAP로고.png';
import { uploadVideoFile } from '../services/videoApi';

function SavedVideoPage() {
  const navigate = useNavigate();
  const fileInputRef = useRef(null);
  const [selectedVideo, setSelectedVideo] = useState(null);
  const [previewUrl, setPreviewUrl] = useState('');
  const [uploadedVideoName, setUploadedVideoName] = useState('');
  const [uploadError, setUploadError] = useState('');
  const [isUploading, setIsUploading] = useState(false);

  useEffect(
    () => () => {
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
      }
    },
    [previewUrl],
  );

  const handleAddVideo = () => {
    fileInputRef.current?.click();
  };

  const handleVideoSelect = (event) => {
    const [file] = event.target.files;

    if (!file) {
      return;
    }

    if (!file.type.startsWith('video/')) {
      setSelectedVideo(null);
      setPreviewUrl('');
      setUploadedVideoName('');
      setUploadError('동영상 파일만 추가할 수 있습니다.');
      event.target.value = '';
      return;
    }

    setSelectedVideo(file);
    setPreviewUrl(URL.createObjectURL(file));
    setUploadedVideoName('');
    setUploadError('');
  };

  const handleSaveVideo = async () => {
    if (!selectedVideo) {
      setUploadError('추가할 동영상을 먼저 선택해주세요.');
      return;
    }

    setIsUploading(true);
    setUploadError('');
    setUploadedVideoName('');

    try {
      const uploadedVideo = await uploadVideoFile(selectedVideo);

      setUploadedVideoName(uploadedVideo?.name || selectedVideo.name);
    } catch (error) {
      setUploadError(error.message || '동영상 업로드에 실패했습니다.');
    } finally {
      setIsUploading(false);
    }
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
        <div className="upload-analysis-box">
          <h1>동영상 업로드 및 분석</h1>

          <div className="video-preview-area">
            {previewUrl ? (
              <video
                className="uploaded-video-preview"
                src={previewUrl}
                controls
                playsInline
              />
            ) : (
              <div className="video-empty-state">동영상을 선택해주세요</div>
            )}
          </div>

          {(selectedVideo || uploadedVideoName || uploadError) && (
            <p
              className={`upload-status ${uploadError ? 'is-error' : ''}`}
              role="status"
            >
              {uploadError ||
                (uploadedVideoName
                  ? `${uploadedVideoName} 저장 완료`
                  : selectedVideo.name)}
            </p>
          )}

          <input
            ref={fileInputRef}
            className="video-file-input"
            type="file"
            accept="video/*"
            onChange={handleVideoSelect}
          />

          <div className="video-action-buttons">
            <button
              className="video-action-btn"
              type="button"
              onClick={handleAddVideo}
              disabled={isUploading}
            >
              추가하기
            </button>

            <button
              className="video-action-btn"
              type="button"
              onClick={handleSaveVideo}
              disabled={!selectedVideo || isUploading}
            >
              {isUploading ? '업로드 중' : '저장하기'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default SavedVideoPage;
