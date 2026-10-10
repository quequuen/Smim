/** 서버 응답 형태. 근거: docs/api.md */

export type QuotedMessage = {
  id: number;
  /** 원글이 삭제·숨김 처리되면 null — 지운 글이 인용으로 남아 읽히면 안 된다 */
  content: string | null;
  createdAt: string;
};

export type Message = {
  id: number;
  content: string;
  /** 0~23. 도형·색 매핑은 앱이 한다 (lib/marker.ts) */
  marker: number;
  /** 서버가 계산해서 내려준다. author_key 는 어떤 응답에도 포함되지 않는다 */
  isMine: boolean;
  createdAt: string;
  replyTo: QuotedMessage | null;
};

export type MessagePage = {
  messages: Message[];
  /** 더 없으면 null */
  nextCursor: string | null;
};

export type RuntimeConfig = {
  group: string;
  radiusM: number;
  keepRadiusM: number;
  moveThresholdM: number;
  heartbeatSec: number;
  markerWindowHour: number;
};

export const DEFAULT_CONFIG: RuntimeConfig = {
  group: 'C',
  radiusM: 300,
  keepRadiusM: 450,
  moveThresholdM: 45,
  heartbeatSec: 45,
  markerWindowHour: 6,
};

/** 내 글에 달린 답글 (D6) */
export type Reply = {
  id: number;
  content: string;
  marker: number;
  createdAt: string;
  myMessage: { id: number; content: string };
};

export type ReportReason = 'abuse' | 'spam' | 'privacy' | 'other';

export const REPORT_REASONS: { value: ReportReason; label: string }[] = [
  { value: 'abuse', label: '욕설 · 비하 · 혐오' },
  { value: 'spam', label: '도배 · 광고' },
  { value: 'privacy', label: '실명 · 연락처 · 주소 노출' },
  { value: 'other', label: '기타' },
];

/**
 * 차단 목록의 한 줄. 작성자를 보여줄 수 없으므로(D4) 차단할 때 지목한 글을 함께 보여준다.
 */
export type Block = {
  id: number;
  /** 차단할 때 지목한 글. 그 글이 지워졌으면 null */
  messageContent: string | null;
  createdAt: string;
};

/** 본문 길이 제한 — 서버의 VARCHAR(500) 과 맞춘다 */
export const CONTENT_MAX = 500;
