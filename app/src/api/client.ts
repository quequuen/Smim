import type { Block, Message, MessagePage, Reply, ReportReason, RuntimeConfig } from './types';

export type Coords = { lat: number; lon: number };

/**
 * 화면 코드는 이 인터페이스만 본다.
 * 서버가 생기면 httpClient 를 추가하고 index.ts 한 줄만 바꾼다.
 */
export interface ApiClient {
  getConfig(): Promise<RuntimeConfig>;
  getMessages(args: { at: Coords; radiusM: number; cursor?: string | null }): Promise<MessagePage>;
  /** 서버가 표식을 확정해 돌려준다 — 화면은 응답으로 받은 글을 그대로 붙인다 */
  postMessage(args: { at: Coords; content: string; replyToId?: number }): Promise<Message>;
  /** 본인 글만. 남의 글이면 NOT_OWNER */
  deleteMessage(id: number): Promise<void>;
  /** 신고한 사람에게는 즉시 보이지 않는다. 같은 글 중복 신고는 ALREADY_REPORTED */
  reportMessage(id: number, reason: ReportReason): Promise<void>;
  /** author_key 를 노출하지 않으므로 메시지로 작성자를 지목한다 */
  blockAuthor(messageId: number): Promise<void>;
  getBlocks(): Promise<Block[]>;
  unblock(blockId: number): Promise<void>;
  /** 내 글에 달린 답글. 읽음 여부는 앱이 로컬 시각으로 판단한다 */
  getReplies(): Promise<Reply[]>;
  /** 하트비트. 실시간 전달 대상에 포함되기 위한 위치 등록 — 화면 갱신과 무관 */
  sendPresence(args: { at: Coords; sessionId: string }): Promise<void>;
}
