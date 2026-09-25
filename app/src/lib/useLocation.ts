import * as Location from 'expo-location';
import { useEffect, useState } from 'react';
import type { Coords } from '../api';

/**
 * 앱이 열려 있는 동안만 현재 위치를 받는다. 근거: docs/location-policy.md 2장
 *
 * - 백그라운드 권한은 요청하지 않는다
 * - 타이머로 폴링하지 않고 OS 거리 필터(distanceInterval)에 맡긴다
 * - accuracy 가 나쁜 측정은 버린다
 *
 * 좌표 스무딩(이동 평균)은 야외 테스트(#14) 로그를 보고 붙인다.
 */

/** 이보다 오차가 큰 측정은 버린다 (m). 도심 GPS 오차 10~20m 에 여유를 둔 값 */
const MAX_ACCURACY_M = 100;

export type LocationState =
  | { status: 'pending' }
  | { status: 'denied' }
  | { status: 'error' }
  | { status: 'ready'; coords: Coords; accuracyM: number | null };

export function useLocation(distanceIntervalM: number): LocationState {
  const [state, setState] = useState<LocationState>({ status: 'pending' });

  useEffect(() => {
    let alive = true;
    let sub: Location.LocationSubscription | null = null;

    (async () => {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (!alive) return;
      if (status !== 'granted') {
        setState({ status: 'denied' });
        return;
      }

      const onFix = (loc: Location.LocationObject) => {
        const { latitude, longitude, accuracy } = loc.coords;
        if (accuracy != null && accuracy > MAX_ACCURACY_M) return;
        setState({ status: 'ready', coords: { lat: latitude, lon: longitude }, accuracyM: accuracy });
      };

      // watch 의 첫 콜백은 느릴 수 있어 마지막 위치로 화면을 먼저 채운다
      const last = await Location.getLastKnownPositionAsync({ maxAge: 5 * 60_000 });
      if (alive && last) onFix(last);

      const watcher = await Location.watchPositionAsync(
        { accuracy: Location.Accuracy.Balanced, distanceInterval: distanceIntervalM },
        onFix,
      );
      if (alive) sub = watcher;
      else watcher.remove();
    })().catch(() => {
      if (alive) setState({ status: 'error' });
    });

    return () => {
      alive = false;
      sub?.remove();
    };
  }, [distanceIntervalM]);

  return state;
}
