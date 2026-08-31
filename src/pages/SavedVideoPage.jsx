import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import '../styles/SavedVideoPage.css';
import logo from '../assets/png/APAP로고.png';
import {
  fetchVideos,
  requestVideoAnalysis,
  uploadVideoFile,
} from '../services/videoApi';

function SavedVideoPage() {
  const navigate = useNavigate();
  const fileInputRef = useRef(null);
  const [selectedVideo, setSelectedVideo] = useState(null);
  const [previewUrl, setPreviewUrl] = useState('');
  const [videos, setVideos] = useState([]);
  const [uploadedVideoName, setUploadedVideoName] = useState('');
  const [uploadError, setUploadError] = useState('');
  const [analysisMessage, setAnalysisMessage] = useState('');
  const [isUploading, setIsUploading] = useState(false);
  const [isLoadingVideos, setIsLoadingVideos] = useState(true);
  const [videoListError, setVideoListError] = useState('');
  const [analyzingVideoId, setAnalyzingVideoId] = useState(null);

  const loadVideos = useCallback(async () => {
    setIsLoadingVideos(true);
    setVideoListError('');

    try {
      const loadedVideos = await fetchVideos();

      setVideos(loadedVideos);
    } catch (error) {
      setVideoListError(error.message || '저장된 영상 목록을 불러오지 못했습니다.');
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
          setVideoListError(
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
    setAnalysisMessage('');
  };

  const handleSaveVideo = async () => {
    if (!selectedVideo) {
      setUploadError('추가할 동영상을 먼저 선택해주세요.');
      return;
    }

    setIsUploading(true);
    setUploadError('');
    setUploadedVideoName('');
    setAnalysisMessage('');

    try {
      const uploadedVideo = await uploadVideoFile(selectedVideo);

      setUploadedVideoName(uploadedVideo?.name || selectedVideo.name);

      if (uploadedVideo) {
        setVideos((currentVideos) => [
          uploadedVideo,
          ...currentVideos.filter((video) => video.id !== uploadedVideo.id),
        ]);
      }
    } catch (error) {
      setUploadError(error.message || '동영상 업로드에 실패했습니다.');
    } finally {
      setIsUploading(false);
    }
  };

  const handleAnalyzeVideo = async (videoId) => {
    setAnalyzingVideoId(videoId);
    setUploadError('');
    setAnalysisMessage('');

    try {
      const job = await requestVideoAnalysis(videoId);

      if (job?.status === 'FAILED') {
        setUploadError(job.errorMessage || 'AI 분석에 실패했습니다.');
        return;
      }

      setAnalysisMessage(
        `분석 작업 #${job?.id ?? videoId} ${job?.status === 'DONE' ? '완료' : '요청 완료'}`,
      );
    } catch (error) {
      setUploadError(error.message || '분석 요청에 실패했습니다.');
    } finally {
      setAnalyzingVideoId(null);
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
        <div className="saved-video-layout">
          <section className="upload-analysis-box">
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

            {(selectedVideo || uploadedVideoName || uploadError || analysisMessage) && (
              <p
                className={`upload-status ${uploadError ? 'is-error' : ''}`}
                role="status"
              >
                {uploadError ||
                  analysisMessage ||
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
                disabled={isUploading || analyzingVideoId !== null}
              >
                추가하기
              </button>

              <button
                className="video-action-btn"
                type="button"
                onClick={handleSaveVideo}
                disabled={!selectedVideo || isUploading || analyzingVideoId !== null}
              >
                {isUploading ? '업로드 중' : '저장하기'}
              </button>
            </div>
          </section>

          <section className="saved-video-list-panel">
            <div className="saved-video-list-header">
              <h2>저장된 영상</h2>

              <button
                className="video-refresh-btn"
                type="button"
                onClick={loadVideos}
                disabled={isLoadingVideos || isUploading}
              >
                새로고침
              </button>
            </div>

            {isLoadingVideos ? (
              <div className="saved-video-empty">목록을 불러오는 중입니다.</div>
            ) : videoListError ? (
              <div className="saved-video-empty is-error">{videoListError}</div>
            ) : videos.length === 0 ? (
              <div className="saved-video-empty">저장된 영상이 없습니다.</div>
            ) : (
              <div className="saved-video-list">
                {videos.map((video) => (
                  <article className="saved-video-item" key={video.id}>
                    <div className="saved-video-info">
                      <strong>{video.name}</strong>
                      <span>{video.sourceUrl}</span>
                      <em>{video.status}</em>
                    </div>

                    <button
                      className="video-analyze-btn"
                      type="button"
                      onClick={() => handleAnalyzeVideo(video.id)}
                      disabled={analyzingVideoId !== null || isUploading}
                    >
                      {analyzingVideoId === video.id ? '분석 중' : '분석'}
                    </button>
                  </article>
                ))}
              </div>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}

export default SavedVideoPage;
