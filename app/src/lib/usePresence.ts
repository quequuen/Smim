import { useEffect, useRef, useState } from 'react';
import { api, type Coords } from '../api';
import { SESSION_ID } from './session';

/**
 * 서버 위치 등록 (하트비트). 근거: docs/location-policy.md 2장
 *
 * 화면 재조회와 목적이 다르므로 분리한다 — 이쪽은 이동 거리와 무관하게 heartbeatSec 주기로 보낸다.
 * 첫 좌표를 받는 즉시 한 번 보내고, 이후에는 주기마다 가장 최근 좌표를 보낸다.
 *
 * 응답으로 오는 "이 근처 몇 명"(nearby)도 여기서 들고 있는다. 같은 호출이 주는 값이고,
 * 그 주기가 곧 "지금 있다" 의 정의이기 때문이다.
 */

export type PresenceState =
  | { status: 'idle'; nearby: null }
  | { status: 'ok'; at: Date; nearby: number | null }
  | { status: 'failed'; error: unknown; nearby: number | null };

export function usePresence(coords: Coords | null, heartbeatSec: number): PresenceState {
  const [state, setState] = useState<PresenceState>({ status: 'idle', nearby: null });
  const latest = useRef(coords);
  latest.current = coords;

  const hasFix = coords !== null;

  useEffect(() => {
    if (!hasFix) {
      // 위치를 잃으면 인원도 모르는 것이 된다. 마지막 숫자를 그대로 두면 다른 자리에서도 그 값이 보인다
      setState({ status: 'idle', nearby: null });
      return;
    }
    let alive = true;

    const beat = () => {
      const at = latest.current;
      if (!at) return;
      api
        .sendPresence({ at, sessionId: SESSION_ID })
        .then((res) => alive && setState({ status: 'ok', at: new Date(), nearby: res.nearby }))
        .catch((error) => {
          if (!alive) return;
          console.warn('[presence]', error);
          // 한 번 실패했다고 숫자를 지우지 않는다 — 다음 주기(heartbeatSec)에 고쳐진다.
          // 깜빡이는 것보다 몇십 초 늦은 숫자가 낫다
          setState((prev) => ({ status: 'failed', error, nearby: prev.nearby }));
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
