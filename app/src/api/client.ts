import type { MessagePage, RuntimeConfig } from './types';

export type Coords = { lat: number; lon: number };

/**
 * 화면 코드는 이 인터페이스만 본다.
 * 서버가 생기면 httpClient 를 추가하고 index.ts 한 줄만 바꾼다.
 */
export interface ApiClient {
  getConfig(): Promise<RuntimeConfig>;
  getMessages(args: { at: Coords; radiusM: number; cursor?: string | null }): Promise<MessagePage>;
  postMessage(args: { at: Coords; content: string; replyToId?: number }): Promise<void>;
  /** 하트비트. 실시간 전달 대상에 포함되기 위한 위치 등록 — 화면 갱신과 무관 */
  sendPresence(args: { at: Coords; sessionId: string }): Promise<void>;
}
