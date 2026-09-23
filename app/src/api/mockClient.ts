import type { ApiClient } from './client';
import { DEFAULT_CONFIG, type Message, type MessagePage } from './types';

/**
 * 서버가 생기기 전까지 쓰는 고정 데이터.
 *
 * 시간 구분선 규칙이 검증되도록 만들었다 — 3블록, 그중 하나는 3개월 이상 벌어진다.
 * 오래된 날짜는 계절 맥락(벚꽃·눈·단풍)이 맞도록 고정했고,
 * 최근 블록만 "어제"가 나오도록 실행 시점 기준으로 만든다.
 */

/** 빈 화면을 보려면 이 값을 true 로 바꾼다 */
export const MOCK_EMPTY = false;

function yesterdayAt(hour: number, minute: number): string {
  const d = new Date();
  d.setDate(d.getDate() - 1);
  d.setHours(hour, minute, 0, 0);
  return d.toISOString();
}

const SNOW = {
  id: 1280,
  content: '벚꽃 얘기 보고 왔는데 지금은 눈 옴 ㅋㅋㅋ',
  createdAt: '2025-12-03T09:11:00+09:00',
};

const MESSAGES: Message[] = [
  // ── 2025년 4월 · 벚꽃 ──
  { id: 1201, content: '여기 벚꽃 미쳤다', marker: 0, isMine: false, createdAt: '2025-04-12T14:23:00+09:00', replyTo: null },
  { id: 1202, content: 'ㄹㅇ 지금이 절정인 듯', marker: 7, isMine: false, createdAt: '2025-04-12T14:41:00+09:00', replyTo: null },
  { id: 1203, content: '3번 출구 쪽이 더 예뻐요', marker: 0, isMine: false, createdAt: '2025-04-12T15:02:00+09:00', replyTo: null },

  // ── 2025년 12월 · 눈 (8개월 벌어짐 → 넓은 여백) ──
  { id: SNOW.id, content: SNOW.content, marker: 0, isMine: false, createdAt: SNOW.createdAt, replyTo: null },

  // ── 어제 · 단풍 ──
  { id: 1290, content: '저도 그거 보고 왔는데 지금은 단풍 들었어요', marker: 0, isMine: false, createdAt: yesterdayAt(11, 5), replyTo: SNOW },
  { id: 1291, content: '여기 지금 사람 많아요?', marker: 14, isMine: false, createdAt: yesterdayAt(17, 30), replyTo: null },
  { id: 1292, content: '1년 전 사람들 아직 여기 오나', marker: 2, isMine: true, createdAt: yesterdayAt(21, 12), replyTo: null },
];

function delay<T>(value: T, ms = 320): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), ms));
}

export const mockClient: ApiClient = {
  async getConfig() {
    return delay(DEFAULT_CONFIG, 120);
  },

  async getMessages(): Promise<MessagePage> {
    if (MOCK_EMPTY) return delay({ messages: [], nextCursor: null });
    // 서버는 최신순으로 내려준다 (createdAt DESC)
    const sorted = [...MESSAGES].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    return delay({ messages: sorted, nextCursor: null });
  },

  async postMessage() {
    return delay(undefined);
  },
};
