export function formatFileSize(value) {
  if (value == null || value === '') return '정보 없음';
  const bytes = Number(value);
  if (!Number.isFinite(bytes) || bytes < 0) return '정보 없음';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const unit = bytes === 0 ? 0 : Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return (bytes / 1024 ** unit).toLocaleString('ko-KR', { maximumFractionDigits: 2 }) + ' ' + units[unit];
}

const sizeKey = (owner, id) => 'apap:video-size:' + encodeURIComponent(String(owner)) + ':' + id;
export function getCachedVideoSize(owner, id) {
  try {
    const value = localStorage.getItem(sizeKey(owner, id));
    if (value === null) return null;
    const size = Number(value);
    return Number.isFinite(size) && size >= 0 ? size : null;
  } catch { return null; }
}
export function cacheVideoSize(owner, id, size) {
  if (id == null || !Number.isFinite(size) || size < 0) return;
  try { localStorage.setItem(sizeKey(owner, id), String(size)); } catch { /* Storage may be unavailable. */ }
}
