import type { ApiClient } from './client';
import { createHttpClient } from './httpClient';
import { mockClient } from './mockClient';

/**
 * 서버 주소는 app/.env 의 EXPO_PUBLIC_API_URL 로 준다 (app/.env.example 참고).
 * 비어 있으면 전부 mock 이다 — 서버 없이 화면 · 오류 상태를 볼 때 쓴다.
 * 화면 코드는 ApiClient 만 보므로 어느 쪽이든 아무것도 고칠 필요가 없다.
 */
const API_URL = process.env.EXPO_PUBLIC_API_URL;

export const api: ApiClient = API_URL ? createHttpClient(API_URL) : mockClient;

export const isServerConnected = Boolean(API_URL);

export * from './client';
export * from './types';
export * from './errors';
