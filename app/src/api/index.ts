import type { ApiClient } from './client';
import { createHttpClient } from './httpClient';
import { mockClient } from './mockClient';

/**
 * 서버 주소는 app/.env 의 EXPO_PUBLIC_API_URL 로 준다 (app/.env.example 참고).
 *
 *   비어 있음  전부 mock 이다 — 서버 없이 화면 · 오류 상태를 볼 때 쓴다
 *   auto       Metro 가 알려준 주소에서 개발 PC 의 IP 를 꺼내 쓴다 (개발 전용)
 *   URL        그대로 쓴다 — 배포는 이것만 쓴다
 *
 * 화면 코드는 ApiClient 만 보므로 어느 쪽이든 아무것도 고칠 필요가 없다.
 */
const RAW = process.env.EXPO_PUBLIC_API_URL?.trim();

/** 루트 .env 의 SERVER_PORT 와 맞춘다 */
const PORT = process.env.EXPO_PUBLIC_API_PORT?.trim() || '8082';

type DevServerInfo = { url: string; bundleLoadedFromServer: boolean };

/**
 * Metro 의 번들 주소에서 호스트만 꺼낸다 — `http://10.0.1.5:8081/` → `10.0.1.5`
 *
 * Wi-Fi 가 바뀌어 IP 가 달라져도 Metro 는 항상 현재 주소를 알고 있으므로 .env 를 고칠 필요가 없다.
 *
 * `NativeModules.SourceCode` 를 직접 읽으면 안 된다 — New Architecture(bridgeless, SDK 57 기본)에서는
 * 비어 있어 조용히 mock 으로 빠진다. getDevServer 는 TurboModule 스펙을 거치므로 양쪽에서 동작한다.
 */
function metroHost(): string | undefined {
  let dev: DevServerInfo;
  try {
    dev = require('react-native/Libraries/Core/Devtools/getDevServer').default() as DevServerInfo;
  }
  catch {
    return undefined; // RN 내부 경로라 버전이 바뀌면 없을 수 있다
  }
  if (!dev.bundleLoadedFromServer) {
    return undefined; // 배포 빌드 — 번들이 파일에서 왔다
  }
  const host = dev.url.match(/^https?:\/\/([^/:]+)/)?.[1];
  // localhost 는 폰 입장에서 폰 자신이라 쓸 수 없다 (getDevServer 의 폴백값이기도 하다)
  return host && host !== 'localhost' && host !== '127.0.0.1' ? host : undefined;
}

function resolveApiUrl(): string | undefined {
  if (!RAW) {
    return undefined;
  }
  if (RAW !== 'auto') {
    return RAW;
  }
  const host = metroHost();
  return host ? `http://${host}:${PORT}` : undefined;
}

const API_URL = resolveApiUrl();

export const api: ApiClient = API_URL ? createHttpClient(API_URL) : mockClient;

export const isServerConnected = Boolean(API_URL);

export * from './client';
export * from './types';
export * from './errors';
