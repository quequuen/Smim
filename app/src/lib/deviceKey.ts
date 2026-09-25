import { getRandomBytesAsync } from 'expo-crypto';
import * as SecureStore from 'expo-secure-store';

/**
 * 기기 익명 키. 근거: docs/api.md 2장
 *
 * 최초 실행 시 32바이트 난수를 만들어 보관하고, 모든 요청에 X-Device-Key 로 보낸다.
 * 앱을 지우면 키도 사라진다 — 이전 글의 삭제 권한이 사라지는 것은 의도된 동작이다.
 */

const STORE_KEY = 'smim.deviceKey';

let cached: Promise<string> | null = null;

async function loadOrCreate(): Promise<string> {
  const saved = await SecureStore.getItemAsync(STORE_KEY);
  if (saved) return saved;

  const bytes = await getRandomBytesAsync(32);
  const key = btoa(String.fromCharCode(...bytes));
  await SecureStore.setItemAsync(STORE_KEY, key);
  return key;
}

export function getDeviceKey(): Promise<string> {
  if (!cached) {
    // 실패한 Promise 를 붙잡고 있으면 다음 호출도 계속 실패하므로 비운다
    cached = loadOrCreate().catch((e) => {
      cached = null;
      throw e;
    });
  }
  return cached;
}
