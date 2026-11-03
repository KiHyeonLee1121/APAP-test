import { getApiBaseUrl } from '../config/env';

const RETRY_BASE_MS = 1000;
const RETRY_MAX_MS = 15000;

/**
 * 실시간 감지 알림 스트림(SSE) 구독.
 *
 * 브라우저 기본 EventSource는 Authorization 헤더를 붙일 수 없어 토큰을 URL에 노출해야 한다.
 * 그래서 fetch + ReadableStream으로 직접 읽는다. (백엔드 GET /api/alerts/stream)
 *
 * 반환값은 구독 해제 함수다.
 */
export const subscribeAlertStream = ({ accessToken, onAlert, onStatus }) => {
  const controller = new AbortController();
  let retryTimer = null;
  let retryDelay = RETRY_BASE_MS;
  let stopped = false;

  const notify = (status) => {
    if (!stopped) {
      onStatus?.(status);
    }
  };

  const scheduleRetry = () => {
    if (stopped) {
      return;
    }
    retryTimer = setTimeout(connect, retryDelay);
    // 서버가 내려가 있을 때 요청을 몰아치지 않도록 간격을 늘려 간다
    retryDelay = Math.min(retryDelay * 2, RETRY_MAX_MS);
  };

  /** SSE 프레임 하나를 해석한다. 주석(:keep-alive)과 connected 이벤트는 무시한다. */
  const handleFrame = (frame) => {
    let eventName = 'message';
    const dataLines = [];

    for (const line of frame.split('\n')) {
      if (!line || line.startsWith(':')) {
        continue;
      }
      if (line.startsWith('event:')) {
        eventName = line.slice('event:'.length).trim();
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice('data:'.length).trim());
      }
    }

    if (eventName !== 'alert' || dataLines.length === 0) {
      return;
    }

    try {
      onAlert?.(JSON.parse(dataLines.join('\n')));
    } catch {
      // 잘린 프레임은 버린다. 다음 알림이나 폴링으로 복구된다.
    }
  };

  async function connect() {
    if (stopped) {
      return;
    }

    try {
      const response = await fetch(`${getApiBaseUrl()}/api/alerts/stream`, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: 'text/event-stream',
        },
        signal: controller.signal,
      });

      if (!response.ok || !response.body) {
        throw new Error(`알림 스트림 연결 실패 (${response.status})`);
      }

      notify('connected');
      retryDelay = RETRY_BASE_MS;

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      for (;;) {
        const { value, done } = await reader.read();
        if (done) {
          break;
        }

        buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n');

        // 프레임은 빈 줄로 구분된다
        let boundary = buffer.indexOf('\n\n');
        while (boundary !== -1) {
          handleFrame(buffer.slice(0, boundary));
          buffer = buffer.slice(boundary + 2);
          boundary = buffer.indexOf('\n\n');
        }
      }

      // 서버가 연결을 닫았다(30분 만료 등) → 다시 붙는다
      notify('reconnecting');
      scheduleRetry();
    } catch {
      if (stopped || controller.signal.aborted) {
        return;
      }
      notify('reconnecting');
      scheduleRetry();
    }
  }

  connect();

  return () => {
    stopped = true;
    clearTimeout(retryTimer);
    controller.abort();
  };
};
