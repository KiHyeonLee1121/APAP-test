import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import '../styles/SavedVideoPage.css';
import logo from '../assets/png/APAP로고.png';
import { useAuth } from '../hooks/useAuth';
import {
  fetchVideoContent,
  fetchVideos,
  uploadVideoFile,
} from '../services/videoApi';

const VIDEO_STATUS_LABELS = {
  READY: '저장 완료',
  PROCESSING: '분석 중',
  FAILED: '분석 실패',
};

const getVideoTitle = (video) => video?.title || video?.name || '제목 없음';

const formatCreatedAt = (value) => {
  if (!value) {
    return '-';
  }

  if (typeof value === 'string') {
    const match = value.match(
      /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})/,
    );

    if (match) {
      return `${match[1]}-${match[2]}-${match[3]} ${match[4]}:${match[5]}`;
    }
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return String(value);
  }

  const pad = (number) => String(number).padStart(2, '0');

  return [
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate()),
  ].join('-') + ` ${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

function SavedVideoPage() {
  const navigate = useNavigate();
  const fileInputRef = useRef(null);
  const previewUrlRef = useRef('');
  const { user: currentUser } = useAuth();

  const [videos, setVideos] = useState([]);
  const [previewVideo, setPreviewVideo] = useState(null);
  const [selectedFile, setSelectedFile] = useState(null);
  const [videoTitle, setVideoTitle] = useState('');
  const [isUploadModalOpen, setIsUploadModalOpen] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [isLoadingVideos, setIsLoadingVideos] = useState(true);
  const [isLoadingPlayback, setIsLoadingPlayback] = useState(false);
  const [isSavedVideoPlaying, setIsSavedVideoPlaying] = useState(false);
  const [selectedVideoId, setSelectedVideoId] = useState(null);
  const [statusMessage, setStatusMessage] = useState('');
  const [errorMessage, setErrorMessage] = useState('');

  const authorName = currentUser?.name || currentUser?.email || '사용자';

  const revokePreviewUrl = useCallback(() => {
    if (previewUrlRef.current) {
      URL.revokeObjectURL(previewUrlRef.current);
      previewUrlRef.current = '';
    }
  }, []);

  const setPreview = useCallback(
    (nextPreview) => {
      revokePreviewUrl();

      if (nextPreview?.url) {
        previewUrlRef.current = nextPreview.url;
      }

      setPreviewVideo(nextPreview);
    },
    [revokePreviewUrl],
  );

  const loadVideos = useCallback(async () => {
    setIsLoadingVideos(true);
    setErrorMessage('');

    try {
      const loadedVideos = await fetchVideos();

      setVideos(loadedVideos);
    } catch (error) {
      setErrorMessage(error.message || '저장된 영상 목록을 불러오지 못했습니다.');
    } finally {
      setIsLoadingVideos(false);
    }
  }, []);

  useEffect(() => {
    let isCancelled = false;

    fetchVideos()
      .then((loadedVideos) => {
        if (!isCancelled) {
          setVideos(loadedVideos);
        }
      })
      .catch((error) => {
        if (!isCancelled) {
          setErrorMessage(
            error.message || '저장된 영상 목록을 불러오지 못했습니다.',
          );
        }
      })
      .finally(() => {
        if (!isCancelled) {
          setIsLoadingVideos(false);
        }
      });

    return () => {
      isCancelled = true;
    };
  }, []);

  useEffect(() => () => revokePreviewUrl(), [revokePreviewUrl]);

  const resetUploadForm = ({ clearMessages = true } = {}) => {
    setSelectedFile(null);
    setVideoTitle('');

    if (clearMessages) {
      setStatusMessage('');
      setErrorMessage('');
    }

    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const openUploadModal = () => {
    if (isSavedVideoPlaying) {
      return;
    }

    resetUploadForm();
    setIsUploadModalOpen(true);
  };

  const closeUploadModal = () => {
    if (isUploading) {
      return;
    }

    setIsUploadModalOpen(false);
    resetUploadForm();
  };

  const handleVideoSelect = (event) => {
    const [file] = event.target.files;

    if (!file) {
      return;
    }

    if (!file.type.startsWith('video/')) {
      setSelectedFile(null);
      setErrorMessage('동영상 파일만 추가할 수 있습니다.');
      event.target.value = '';
      return;
    }

    setSelectedFile(file);
    setVideoTitle((currentTitle) => currentTitle || file.name);
    setErrorMessage('');
  };

  const handleSaveVideo = async (event) => {
    event.preventDefault();

    if (!selectedFile) {
      setErrorMessage('저장할 동영상을 선택해주세요.');
      return;
    }

    const trimmedTitle = videoTitle.trim();

    if (!trimmedTitle) {
      setErrorMessage('제목을 입력해주세요.');
      return;
    }

    setIsUploading(true);
    setErrorMessage('');
    setStatusMessage('');

    try {
      const uploadedVideo = await uploadVideoFile(selectedFile, {
        name: trimmedTitle,
      });

      if (uploadedVideo) {
        setVideos((currentVideos) => [
          {
            ...uploadedVideo,
            author: uploadedVideo.author || authorName,
          },
          ...currentVideos.filter((video) => video.id !== uploadedVideo.id),
        ]);
      }

      setPreview({
        type: 'upload',
        title: trimmedTitle,
        author: authorName,
        createdAt: uploadedVideo?.createdAt,
        url: URL.createObjectURL(selectedFile),
      });
      setSelectedVideoId(uploadedVideo?.id ?? null);
      setIsSavedVideoPlaying(false);
      setStatusMessage('영상이 저장되었습니다. 백엔드에서 자동 분석이 시작됩니다.');
      setIsUploadModalOpen(false);
      resetUploadForm({ clearMessages: false });
    } catch (error) {
      setErrorMessage(error.message || '동영상 저장에 실패했습니다.');
    } finally {
      setIsUploading(false);
    }
  };

  const handleSavedVideoClick = async (video) => {
    if (!video?.id || isLoadingPlayback) {
      return;
    }

    setSelectedVideoId(video.id);
    setIsLoadingPlayback(true);
    setIsSavedVideoPlaying(false);
    setStatusMessage('');
    setErrorMessage('');

    try {
      const videoBlob = await fetchVideoContent(video.id);

      setPreview({
        type: 'saved',
        title: getVideoTitle(video),
        author: video.author || authorName,
        createdAt: video.createdAt || video.created_at,
        url: URL.createObjectURL(videoBlob),
      });
    } catch (error) {
      setErrorMessage(error.message || '저장된 영상을 재생할 수 없습니다.');
    } finally {
      setIsLoadingPlayback(false);
    }
  };

  const isAddDisabled = isUploading || isLoadingPlayback || isSavedVideoPlaying;

  return (
    <div className="saved-video-container">
      <div className="top-bar">
        <button className="back-button" onClick={() => navigate('/main')}>
          ←
        </button>

        <img src={logo} alt="APAP" className="top-logo" />
      </div>

      <div className="white-wrapper">
        <div className="saved-video-layout">
          <section className="video-preview-panel">
            <div className="saved-video-heading">
              <h1>저장된 영상</h1>
              <button
                className="video-action-btn"
                type="button"
                onClick={openUploadModal}
                disabled={isAddDisabled}
              >
                추가하기
              </button>
            </div>

            <div className="video-preview-area">
              {previewVideo?.url ? (
                <video
                  className="uploaded-video-preview"
                  src={previewVideo.url}
                  controls
                  playsInline
                  onPlay={() =>
                    setIsSavedVideoPlaying(previewVideo.type === 'saved')
                  }
                  onPause={() => setIsSavedVideoPlaying(false)}
                  onEnded={() => setIsSavedVideoPlaying(false)}
                />
              ) : (
                <div className="video-empty-state">
                  저장된 영상을 선택하거나 새 영상을 추가해주세요
                </div>
              )}
            </div>

            {previewVideo && (
              <dl className="preview-meta">
                <div>
                  <dt>제목</dt>
                  <dd>{previewVideo.title}</dd>
                </div>
                <div>
                  <dt>작성자</dt>
                  <dd>{previewVideo.author}</dd>
                </div>
                <div>
                  <dt>작성 시간</dt>
                  <dd>{formatCreatedAt(previewVideo.createdAt)}</dd>
                </div>
              </dl>
            )}

            {(statusMessage || errorMessage || isLoadingPlayback) && (
              <p
                className={`upload-status ${errorMessage ? 'is-error' : ''}`}
                role="status"
              >
                {errorMessage ||
                  (isLoadingPlayback
                    ? '저장된 영상을 불러오는 중입니다.'
                    : statusMessage)}
              </p>
            )}
          </section>

          <section className="saved-video-list-panel">
            <div className="saved-video-list-header">
              <h2>영상 목록</h2>

              <button
                className="video-refresh-btn"
                type="button"
                onClick={loadVideos}
                disabled={isLoadingVideos || isUploading || isLoadingPlayback}
              >
                새로고침
              </button>
            </div>

            {isLoadingVideos ? (
              <div className="saved-video-empty">목록을 불러오는 중입니다.</div>
            ) : errorMessage && videos.length === 0 ? (
              <div className="saved-video-empty is-error">{errorMessage}</div>
            ) : videos.length === 0 ? (
              <div className="saved-video-empty">저장된 영상이 없습니다.</div>
            ) : (
              <div className="saved-video-list">
                <div className="saved-video-row saved-video-row-header">
                  <span>제목</span>
                  <span>작성자</span>
                  <span>작성 시간</span>
                  <span>상태</span>
                </div>

                {videos.map((video) => (
                  <button
                    className={`saved-video-row ${
                      selectedVideoId === video.id ? 'is-selected' : ''
                    }`}
                    key={video.id}
                    type="button"
                    onClick={() => handleSavedVideoClick(video)}
                    disabled={isLoadingPlayback}
                  >
                    <span>{getVideoTitle(video)}</span>
                    <span>{video.author || authorName}</span>
                    <span>{formatCreatedAt(video.createdAt || video.created_at)}</span>
                    <span>{VIDEO_STATUS_LABELS[video.status] || video.status || '-'}</span>
                  </button>
                ))}
              </div>
            )}
          </section>
        </div>
      </div>

      {isUploadModalOpen && (
        <div className="upload-modal-overlay">
          <form className="upload-modal" onSubmit={handleSaveVideo}>
            <h2>영상 추가</h2>

            <label className="upload-field">
              <span>제목</span>
              <input
                type="text"
                value={videoTitle}
                onChange={(event) => setVideoTitle(event.target.value)}
                placeholder="영상 제목"
                disabled={isUploading}
              />
            </label>

            <label className="upload-field">
              <span>작성자</span>
              <input type="text" value={authorName} readOnly />
            </label>

            <label className="upload-field">
              <span>영상 파일</span>
              <input
                ref={fileInputRef}
                type="file"
                accept="video/*"
                onChange={handleVideoSelect}
                disabled={isUploading}
              />
            </label>

            {selectedFile && (
              <p className="selected-file-name">{selectedFile.name}</p>
            )}

            {errorMessage && (
              <p className="modal-error" role="alert">
                {errorMessage}
              </p>
            )}

            <div className="upload-modal-buttons">
              <button type="button" onClick={closeUploadModal} disabled={isUploading}>
                취소
              </button>
              <button type="submit" disabled={isUploading}>
                {isUploading ? '저장 중' : '저장하기'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}

export default SavedVideoPage;
