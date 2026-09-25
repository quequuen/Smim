import type { ApiClient } from './client';
import { createHttpClient } from './httpClient';
import { mockClient } from './mockClient';

/**
 * 서버 주소는 app/.env 의 EXPO_PUBLIC_API_URL 로 준다 (app/.env.example 참고).
 * 비어 있으면 전부 mock 이다.
 *
 * 서버에 아직 있는 것만 http 로 보낸다. 엔드포인트가 생길 때마다 한 줄씩 옮기고,
 * 전부 생기면 `API_URL ? createHttpClient(API_URL) : mockClient` 로 합친다.
 * 화면 코드는 ApiClient 만 보므로 아무것도 고칠 필요가 없다.
 */
const API_URL = process.env.EXPO_PUBLIC_API_URL;

const http = API_URL ? createHttpClient(API_URL) : null;

export const api: ApiClient = http
  ? {
      ...mockClient,
      sendPresence: http.sendPresence, // #7
    }
  : mockClient;

export const isServerConnected = http !== null;

export * from './client';
export * from './types';
export { ApiError } from './httpClient';
