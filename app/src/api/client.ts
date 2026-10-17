import type { Block, Message, MessagePage, Reply, ReportReason, RuntimeConfig } from './types';

export type Coords = { lat: number; lon: number };

export type LiveStatus = 'connecting' | 'live' | 'closed';

/** 실시간 수신 (WebSocket /ws). 근거: docs/api.md */
export type LiveOptions = {
  sessionId: string;
  /** 연결할 때마다 부른다 — 재연결 시 최신 좌표로 바로 등록되도록 */
  getCoords: () => Coords | null;
  /** 반경 안에서 남이 쓴 글. 형태는 GET /messages 의 항목과 같다 */
  onMessage: (m: Message) => void;
  /** 끊겼다가 다시 붙었다 — 그사이 놓친 글을 GET /messages 로 보충한다 */
  onReconnected: () => void;
  onStatus: (s: LiveStatus) => void;
};

export type LiveConnection = {
  /** 연결을 시작한다. 끊기면 알아서 다시 붙는다 */
  start(): void;
  /** 백그라운드로 갈 때. 서버는 연결 종료를 보고 위치를 바로 지운다 */
  stop(): void;
};

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
  /** 실시간 수신. mock 은 아무것도 하지 않는다 */
  connectLive(opts: LiveOptions): LiveConnection;
}
