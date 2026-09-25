import { randomUUID } from 'expo-crypto';
import { useEffect, useRef, useState } from 'react';
import { api, type Coords } from '../api';

/**
 * 서버 위치 등록 (하트비트). 근거: docs/location-policy.md 2장
 *
 * 화면 재조회와 목적이 다르므로 분리한다 — 이쪽은 이동 거리와 무관하게 heartbeatSec 주기로 보낸다.
 * 첫 좌표를 받는 즉시 한 번 보내고, 이후에는 주기마다 가장 최근 좌표를 보낸다.
 */

/** 앱을 켤 때마다 새로 만든다. 기기 키와 달리 저장하지 않는다 */
const SESSION_ID = randomUUID();

export type PresenceState = { status: 'idle' } | { status: 'ok'; at: Date } | { status: 'failed'; error: unknown };

export function usePresence(coords: Coords | null, heartbeatSec: number): PresenceState {
  const [state, setState] = useState<PresenceState>({ status: 'idle' });
  const latest = useRef(coords);
  latest.current = coords;

  const hasFix = coords !== null;

  useEffect(() => {
    if (!hasFix) return;
    let alive = true;

    const beat = () => {
      const at = latest.current;
      if (!at) return;
      api
        .sendPresence({ at, sessionId: SESSION_ID })
        .then(() => alive && setState({ status: 'ok', at: new Date() }))
        .catch((error) => {
          if (!alive) return;
          console.warn('[presence]', error);
          setState({ status: 'failed', error });
        });
    };

    beat();
    const timer = setInterval(beat, heartbeatSec * 1000);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, [hasFix, heartbeatSec]);

  return state;
}
