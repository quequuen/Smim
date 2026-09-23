/** 서버 응답 형태. 근거: docs/api.md */

export type QuotedMessage = {
  id: number;
  content: string;
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
