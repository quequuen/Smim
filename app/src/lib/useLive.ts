import { useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';
import { api, type Coords, type LiveStatus, type Message } from '../api';
import { SESSION_ID } from './session';

/**
 * 반경 안에서 새 글이 쓰이면 서버가 밀어준다 (D5).
 *
 * 앱이 앞에 있을 때만 연결한다. 백그라운드로 가면 끊고(서버가 위치를 바로 지운다),
 * 돌아오면 다시 붙으면서 onReconnected 로 그사이 놓친 글을 보충하게 한다.
 * 첫 좌표가 잡히기 전에는 연결하지 않는다 — 위치 없이 붙으면 받을 수 있는 글이 없다.
 */
export function useLive(
  coords: Coords | null,
  handlers: { onMessage: (m: Message) => void; onReconnected: () => void },
): LiveStatus {
  const [status, setStatus] = useState<LiveStatus>('closed');

  // 연결은 한 번만 만들고, 좌표와 콜백은 최신 값을 읽게 한다
  const latest = useRef({ coords, handlers });
  latest.current = { coords, handlers };

  const hasFix = coords !== null;

  useEffect(() => {
    if (!hasFix) return;
    const live = api.connectLive({
      sessionId: SESSION_ID,
      getCoords: () => latest.current.coords,
      onMessage: (m) => latest.current.handlers.onMessage(m),
      onReconnected: () => latest.current.handlers.onReconnected(),
      onStatus: setStatus,
    });

    live.start();
    const sub = AppState.addEventListener('change', (s) => {
      if (s === 'active') live.start();
      else if (s === 'background') live.stop();
    });
    return () => {
      sub.remove();
      live.stop();
    };
  }, [hasFix]);

  return status;
}
