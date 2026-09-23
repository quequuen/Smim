import type { ApiClient } from './client';
import { mockClient } from './mockClient';

/**
 * 서버가 생기면 httpClient 를 만들고 이 줄만 바꾼다.
 * 화면 코드는 ApiClient 만 보므로 아무것도 고칠 필요가 없다.
 */
export const api: ApiClient = mockClient;

export * from './client';
export * from './types';
